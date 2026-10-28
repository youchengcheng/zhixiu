package com.ycc.zhixiucloud.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ycc.zhixiucloud.common.enums.RoleEnum;
import com.ycc.zhixiucloud.common.exception.BizException;
import com.ycc.zhixiucloud.common.result.PageResult;
import com.ycc.zhixiucloud.common.result.ResultCode;
import com.ycc.zhixiucloud.entity.Property;
import com.ycc.zhixiucloud.entity.Role;
import com.ycc.zhixiucloud.entity.User;
import com.ycc.zhixiucloud.mapper.PropertyMapper;
import com.ycc.zhixiucloud.mapper.RoleMapper;
import com.ycc.zhixiucloud.mapper.UserMapper;
import com.ycc.zhixiucloud.request.OwnerQueryRequestVO;
import com.ycc.zhixiucloud.response.OwnerListResponseVO;
import com.ycc.zhixiucloud.response.PropertyResponseVO;
import com.ycc.zhixiucloud.service.OwnerService;
import com.ycc.zhixiucloud.service.PropertyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 业主管理实现：业主即角色为 OWNER 的用户，房产数量按当前页批量统计。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>业主身份通过角色编码 OWNER 动态解析角色 ID，避免在代码中硬编码角色主键；</li>
 *   <li>列表先分页查询用户，再对本页业主 ID 集合一次性批量查询房产并汇总数量，
 *       避免逐条统计造成 N+1 查询；</li>
 *   <li>启用/禁用只更新 user.status，不删除账号和历史数据，禁用后的拦截由登录与
 *       鉴权环节统一处理，本类不做会话踢出；</li>
 *   <li>名下房产列表复用 {@link PropertyService#listByOwner(Long)}，与业主端保持
 *       相同的默认房产优先排序。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OwnerServiceImpl implements OwnerService {

    /** 账号状态取值：0 禁用 1 正常 */
    private static final int STATUS_NORMAL = 1;

    private static final int STATUS_DISABLED = 0;

    private final UserMapper userMapper;

    private final RoleMapper roleMapper;

    private final PropertyMapper propertyMapper;

    private final PropertyService propertyService;

    /**
     * 分页查询业主账号，支持按状态与关键字筛选。
     *
     * <p>仅查询角色为 OWNER 的用户；关键字同时模糊匹配姓名、手机号和登录名。
     * 房产数量先收集当前页业主 ID，再批量查询房产并汇总，最后回填到每个列表项，
     * 避免逐条 count 查询。分页后的总数来自数据库 count，不受房产统计影响。</p>
     *
     * @param requestVO 分页及筛选条件
     * @return 业主分页数据，含名下房产数量
     * @throws BizException 业主角色未初始化时抛出
     */
    @Override
    public PageResult<OwnerListResponseVO> page(OwnerQueryRequestVO requestVO) {
        // 解析 OWNER 角色 ID，避免硬编码
        Long ownerRoleId = requireOwnerRoleId();
        // 组装分页条件：角色、状态、关键字模糊匹配姓名/手机号/登录名
        LambdaQueryWrapper<User> wrapper = Wrappers.<User>lambdaQuery()
                .eq(User::getRoleId, ownerRoleId)
                .eq(requestVO.getStatus() != null, User::getStatus, requestVO.getStatus())
                .and(StringUtils.hasText(requestVO.getKeyword()), w -> w
                        .like(User::getRealName, requestVO.getKeyword())
                        .or()
                        .like(User::getPhone, requestVO.getKeyword())
                        .or()
                        .like(User::getUsername, requestVO.getKeyword()))
                .orderByDesc(User::getCreateTime);
        // 分页查询业主用户
        Page<User> page = userMapper.selectPage(new Page<>(requestVO.getPageNum(), requestVO.getPageSize()), wrapper);
        // 对本页业主 ID 批量统计房产数量，避免 N+1 查询
        Map<Long, Long> propertyCounts = countProperties(page.getRecords().stream()
                .map(User::getId).collect(Collectors.toSet()));

        // 复用分页元数据构造返回页
        Page<OwnerListResponseVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        // 逐条装配展示对象并回填房产数量
        voPage.setRecords(page.getRecords().stream().map(user -> OwnerListResponseVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                // 头像随列表返回，管理端直接展示业主当前头像，未上传时前端回退首字
                .avatar(user.getAvatar())
                .phone(user.getPhone())
                .email(user.getEmail())
                .status(user.getStatus())
                .propertyCount(propertyCounts.getOrDefault(user.getId(), 0L))
                .lastLoginTime(user.getLastLoginTime())
                .createTime(user.getCreateTime())
                .build()).toList());
        // 统一包装为分页结果
        return PageResult.of(voPage);
    }

    /**
     * 启用或禁用业主账号。
     *
     * <p>只允许 1 正常 / 0 禁用，且目标账号必须是业主角色。仅更新 user.status
     * 字段并记录操作日志，不删除账号、不清理房产数据；禁用后的登录拦截在鉴权
     * 环节完成，本方法也不会主动失效已登录会话。</p>
     *
     * @param id     业主用户 ID
     * @param status 目标状态：1 正常 / 0 禁用
     * @throws BizException 状态非法、账号不存在或不是业主时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        // 状态取值只允许 0/1
        if (status == null || (status != STATUS_NORMAL && status != STATUS_DISABLED)) {
            // 仅允许 1 正常 / 0 禁用
            throw BizException.of(ResultCode.PARAM_ERROR, "账号状态取值 0/1");
        }
        // 校验目标账号存在且角色为业主
        User user = requireOwner(id);
        // 只组装主键与状态字段，避免覆盖其它资料
        User update = new User();
        // 指定主键
        update.setId(user.getId());
        // 写入目标状态
        update.setStatus(status);
        // 按主键更新状态字段
        userMapper.updateById(update);
        // 记录状态调整日志
        log.info("业主账号状态调整：userId={}, username={}, status={}", user.getId(), user.getUsername(), status);
    }

    /**
     * 查询指定业主名下的房产列表（管理端）。
     *
     * <p>先校验目标账号确为业主，再委托房产服务按业主 ID 查询；不校验当前登录人
     * 与业主的归属关系，权限由管理端接口的鉴权负责。</p>
     *
     * @param ownerId 业主用户 ID
     * @return 该业主的房产列表
     * @throws BizException 账号不存在或不是业主时抛出
     */
    @Override
    public List<PropertyResponseVO> listProperties(Long ownerId) {
        // 先确认目标账号确为业主
        requireOwner(ownerId);
        // 委托房产服务按业主 ID 查询名下房产
        return propertyService.listByOwner(ownerId);
    }

    /**
     * 批量统计一批业主的房产数量。
     *
     * <p>用一条 IN 查询取出这些业主的全部房产，再在内存中按 ownerId 累加，
     * 将“每行一次 count”降为一次查询；空集合直接返回空 Map，避免生成非法 IN 条件。</p>
     *
     * @param ownerIds 当前页业主 ID 集合
     * @return ownerId 到房产数量的映射，没有房产的业主不会出现在 Map 中
     */
    private Map<Long, Long> countProperties(Set<Long> ownerIds) {
        // 无业主 ID 时直接返回空映射
        if (ownerIds.isEmpty()) {
            // 空集合直接返回，避免生成非法 IN 条件
            return Collections.emptyMap();
        }
        // 一次 IN 查询取出这些业主的全部房产
        List<Property> properties = propertyMapper.selectList(Wrappers.<Property>lambdaQuery()
                .in(Property::getOwnerId, ownerIds));
        // 在内存中按 ownerId 累加房产数量
        Map<Long, Long> counts = new HashMap<>(ownerIds.size());
        // 逐行累加各业主的房产数
        for (Property property : properties) {
            // 同业主计数累加
            counts.merge(property.getOwnerId(), 1L, Long::sum);
        }
        // 返回 ownerId 到房产数量的映射
        return counts;
    }

    /**
     * 按用户 ID 查询账号并确认其角色为业主。
     *
     * <p>先查用户是否存在，再解析 OWNER 角色 ID 做比对，两个校验失败分别返回
     * NOT_FOUND 与 PARAM_ERROR，便于前端区分“账号不存在”和“不是业主”。</p>
     */
    private User requireOwner(Long id) {
        // 先确认账号存在
        User user = userMapper.selectById(id);
        // 账号为空说明不存在
        if (user == null) {
            // 不存在返回 404
            throw BizException.of(ResultCode.NOT_FOUND, "业主账号不存在");
        }
        // 解析 OWNER 角色 ID 做比对
        Long ownerRoleId = requireOwnerRoleId();
        // 角色 ID 不匹配说明不是业主
        if (!ownerRoleId.equals(user.getRoleId())) {
            // 角色不匹配返回参数错误，便于前端区分
            throw BizException.of(ResultCode.PARAM_ERROR, "该账号不是业主");
        }
        // 校验通过，返回业主实体
        return user;
    }

    /**
     * 按角色编码查询业主角色 ID，并校验角色已初始化。
     * 若角色表缺少 OWNER 记录，说明系统初始化数据不完整，返回 SYSTEM_ERROR。
     */
    private Long requireOwnerRoleId() {
        // 按角色编码查询业主角色，避免硬编码角色主键
        Role role = roleMapper.selectOne(Wrappers.<Role>lambdaQuery()
                .eq(Role::getRoleCode, RoleEnum.OWNER.getCode()));
        // 角色缺失说明初始化数据不完整
        if (role == null) {
            // 角色表缺少 OWNER 记录，说明初始化数据不完整
            throw BizException.of(ResultCode.SYSTEM_ERROR, "角色未初始化：" + RoleEnum.OWNER.getCode());
        }
        // 返回业主角色 ID
        return role.getId();
    }
}