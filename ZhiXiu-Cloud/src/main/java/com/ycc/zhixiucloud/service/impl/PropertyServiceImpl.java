package com.ycc.zhixiucloud.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ycc.zhixiucloud.common.enums.RoleEnum;
import com.ycc.zhixiucloud.common.exception.BizException;
import com.ycc.zhixiucloud.common.result.ResultCode;
import com.ycc.zhixiucloud.common.util.SecurityUtils;
import com.ycc.zhixiucloud.entity.Property;
import com.ycc.zhixiucloud.entity.Role;
import com.ycc.zhixiucloud.entity.User;
import com.ycc.zhixiucloud.mapper.PropertyMapper;
import com.ycc.zhixiucloud.mapper.RoleMapper;
import com.ycc.zhixiucloud.mapper.UserMapper;
import com.ycc.zhixiucloud.request.AdminPropertySaveRequestVO;
import com.ycc.zhixiucloud.request.PropertySaveRequestVO;
import com.ycc.zhixiucloud.response.PropertyResponseVO;
import com.ycc.zhixiucloud.service.PropertyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 房产服务实现：业主侧操作限定在当前登录业主名下，管理端操作按入参指定业主。
 *
 * <p>核心规则与设计：</p>
 * <ul>
 *   <li>同一业主可登记多套房产，但最多只有一套默认房产；第一套自动设为默认，
 *       设置为默认时会清除该业主其它房产的默认标记；</li>
 *   <li>业主端新增、修改、删除、设默认均先校验房产归属，管理端可跨业主操作并
 *       支持把房产转移到另一位业主名下（转移前校验目标账号确为业主角色）；</li>
 *   <li>写操作使用 Spring 事务；完整地址由小区、栋、单元、房号拼接，数据库对
 *       (community, building, unit, room) 建有唯一索引，插入/更新冲突时转换为
 *       可读的业务异常；</li>
 *   <li>删除为逻辑删除（deleted=1），不会级联删除历史工单，保证工单中记录的
 *       报修地址仍可追溯。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PropertyServiceImpl implements PropertyService {

    private static final int DEFAULT_YES = 1;

    private static final int DEFAULT_NO = 0;

    private static final int STATUS_NORMAL = 1;

    private final PropertyMapper propertyMapper;

    private final UserMapper userMapper;

    private final RoleMapper roleMapper;

    /**
     * 查询当前登录业主名下的全部房产。
     * 默认房产排在最前，其余按创建时间倒序，便于报修时优先选到常用地址。
     *
     * @return 当前业主的房产列表
     */
    @Override
    public List<PropertyResponseVO> listMine() {
        // 归属人取当前登录业主，复用统一查询逻辑
        return listByOwnerId(SecurityUtils.currentUserId());
    }

    /**
     * 管理端查询指定业主名下的房产，不校验当前登录人归属。
     *
     * @param ownerId 业主用户 ID
     * @return 该业主的房产列表，排序规则与业主端一致
     */
    @Override
    public List<PropertyResponseVO> listByOwner(Long ownerId) {
        // 管理端按入参业主查询，不校验当前登录人归属
        return listByOwnerId(ownerId);
    }

    /**
     * 业主新增房产，归属人固定为当前登录业主。
     *
     * @param requestVO 房产信息（小区、栋、单元、房号、面积、是否默认）
     * @return 新房产 ID
     * @throws BizException 地址重复时抛出 CONFLICT
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(PropertySaveRequestVO requestVO) {
        // 归属人固定为当前登录业主，走公共新增流程
        return doCreate(SecurityUtils.currentUserId(), requestVO);
    }

    /**
     * 管理端代业主新增房产，归属人由入参指定。
     *
     * <p>写入前校验目标用户存在且角色为 OWNER，防止把房产挂到管理员或师傅账号下；
     * 若目标业主尚无房产，则该套自动成为默认房产。</p>
     *
     * @param requestVO 房产信息及归属业主 ID
     * @return 新房产 ID
     * @throws BizException 目标账号不存在、不是业主或地址重复时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createForOwner(AdminPropertySaveRequestVO requestVO) {
        // 校验目标账号确为业主角色，防止挂到管理员或师傅名下
        requireOwnerUser(requestVO.getOwnerId());
        // 以入参指定的业主为归属人新增
        return doCreate(requestVO.getOwnerId(), requestVO);
    }

    /**
     * 业主修改自己的房产信息。
     *
     * <p>先校验房产归属，再更新地址与面积；默认标记只能在设为默认时开启，
     * 不会因为编辑动作被清除。</p>
     *
     * @param id        房产 ID
     * @param requestVO 新的房产信息
     * @throws BizException 房产不存在、不属于当前业主或地址重复时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PropertySaveRequestVO requestVO) {
        // 先校验房产归属当前业主，再刷新信息
        doUpdate(requireOwned(id), requestVO);
    }

    /**
     * 管理端修改房产，不校验当前登录人的归属关系。
     *
     * <p>若入参携带了与原归属不同的 ownerId，则先把房产转移到新业主名下，
     * 转移前校验目标账号为业主角色；未携带 ownerId 时保持原归属不变。</p>
     *
     * @param id        房产 ID
     * @param requestVO 新的房产信息，可选的新归属业主 ID
     * @throws BizException 房产不存在、目标账号不是业主或地址重复时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateByAdmin(Long id, AdminPropertySaveRequestVO requestVO) {
        // 管理端不校验归属，仅确认房产存在
        Property property = requireExists(id);
        // 管理端允许把房产转移到另一位业主名下
        if (requestVO.getOwnerId() != null && !requestVO.getOwnerId().equals(property.getOwnerId())) {
            // 转移前校验新归属账号确为业主
            requireOwnerUser(requestVO.getOwnerId());
            // 先改归属人，后续默认标记清理以新业主为准
            property.setOwnerId(requestVO.getOwnerId());
        }
        // 复用公共修改流程刷新地址与面积
        doUpdate(property, requestVO);
    }

    /**
     * 业主删除自己的房产（逻辑删除）。
     *
     * <p>删除前校验归属；若删除的是默认房产，则自动把该业主最新登记的一套
     * 房产提升为默认，避免出现“有房但无默认地址”的状态。</p>
     *
     * @param id 房产 ID
     * @throws BizException 房产不存在或不属于当前业主时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 先校验归属，再走公共逻辑删除流程
        doDelete(requireOwned(id));
    }

    /**
     * 管理端删除房产（逻辑删除），不校验归属，其余默认房产接替规则同业主端删除。
     *
     * @param id 房产 ID
     * @throws BizException 房产不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByAdmin(Long id) {
        // 管理端不校验归属，仅确认房产存在
        doDelete(requireExists(id));
    }

    /**
     * 把指定房产设为当前业主的默认房产。
     *
     * <p>先将该业主其它房产的默认标记清零，再设置目标房产，保证“一人一套默认”；
     * 两项更新处于同一事务中，避免中途失败留下多套默认房产。</p>
     *
     * @param id 房产 ID
     * @throws BizException 房产不存在或不属于当前业主时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id) {
        // 校验归属并取回房产实体
        Property property = requireOwned(id);
        // 先清除该业主其它房产的默认标记
        clearOtherDefault(property.getOwnerId(), id);
        // 再组装只含主键与默认标记的更新对象
        Property update = new Property();
        // 指定主键
        update.setId(id);
        // 打开默认标记
        update.setIsDefault(DEFAULT_YES);
        // 只更新默认标记，避免覆盖其它字段
        propertyMapper.updateById(update);
        // 记录设为默认日志
        log.info("设为默认房产：ownerId={}, propertyId={}", property.getOwnerId(), id);
    }

    /** 按业主查询房产并转换为展示对象：默认房产优先，其次按创建时间倒序 */
    private List<PropertyResponseVO> listByOwnerId(Long ownerId) {
        // 默认房产优先，其余按创建时间倒序
        List<Property> properties = propertyMapper.selectList(Wrappers.<Property>lambdaQuery()
                .eq(Property::getOwnerId, ownerId)
                .orderByDesc(Property::getIsDefault)
                .orderByDesc(Property::getCreateTime));
        // 实体统一转换为展示对象返回
        return properties.stream().map(this::toVO).toList();
    }

    /**
     * 新增房产的公共流程（业主端与管理端共用）。
     *
     * <p>先统计该业主已有房产数，用于判断是否为第一套；插入成功后，若本套被设为
     * 默认（入参指定或为第一套），再清除同业主其它房产的默认标记。</p>
     *
     * @param ownerId   房产归属业主用户 ID
     * @param requestVO 房产信息
     * @return 新房产 ID
     * @throws BizException 地址重复时抛出 CONFLICT
     */
    private Long doCreate(Long ownerId, PropertySaveRequestVO requestVO) {
        // 统计该业主已有房产数，0 表示这是第一套
        long owned = propertyMapper.selectCount(Wrappers.<Property>lambdaQuery()
                .eq(Property::getOwnerId, ownerId));

        // 组装待落库的房产实体
        Property property = new Property();
        // 归属人由调用方确定（业主端为当前登录业主）
        property.setOwnerId(ownerId);
        // 填充拆分字段并同步拼接完整地址
        fillAddress(property, requestVO);
        // 面积原样落库
        property.setAreaSize(requestVO.getAreaSize());
        // 新登记房产一律为正常状态
        property.setStatus(STATUS_NORMAL);
        // 第一套房产自动设为默认，避免业主没有默认房产
        boolean asDefault = Integer.valueOf(DEFAULT_YES).equals(requestVO.getIsDefault()) || owned == 0;
        // 按判定结果写入默认标记
        property.setIsDefault(asDefault ? DEFAULT_YES : DEFAULT_NO);

        // 插入落库，地址冲突由唯一索引兜底并转业务异常
        insert(property);
        // 本套被设为默认时，需清理同业主其它房产
        if (asDefault) {
            // 清除该业主其它房产的默认标记，保证仅一套默认
            clearOtherDefault(ownerId, property.getId());
        }
        // 记录新增日志
        log.info("新增房产：ownerId={}, propertyId={}, address={}",
                ownerId, property.getId(), property.getFullAddress());
        // 返回新房产主键供前端后续操作
        return property.getId();
    }

    /**
     * 修改房产的公共流程（业主端与管理端共用）。
     *
     * <p>按入参刷新地址与面积；默认标记只能通过入参开启，不会因编辑被取消。
     * 若管理端已把 ownerId 转移到新业主，后续默认标记清理也以新业主为准。
     * 数据库唯一索引冲突（该地址已被登记）统一转换为 CONFLICT 业务异常。</p>
     */
    private void doUpdate(Property property, PropertySaveRequestVO requestVO) {
        // 刷新拆分字段并同步完整地址
        fillAddress(property, requestVO);
        // 面积原样更新
        property.setAreaSize(requestVO.getAreaSize());
        // 默认房产只能被其它房产取代，不能在编辑页取消
        if (Integer.valueOf(DEFAULT_YES).equals(requestVO.getIsDefault())) {
            // 入参显式要求时开启默认标记
            property.setIsDefault(DEFAULT_YES);
        }

        // 更新落库，地址唯一索引冲突时转业务异常
        try {
            // 按主键更新非空字段
            propertyMapper.updateById(property);
        } catch (DuplicateKeyException e) {
            // 该地址已被登记，返回冲突业务码
            throw BizException.of(ResultCode.CONFLICT, "该房产已存在");
        }
        if (Integer.valueOf(DEFAULT_YES).equals(property.getIsDefault())) {
            // 设为默认后清除该业主其它房产的默认标记
            clearOtherDefault(property.getOwnerId(), property.getId());
        }
        // 记录修改日志
        log.info("修改房产：propertyId={}, ownerId={}, address={}",
                property.getId(), property.getOwnerId(), property.getFullAddress());
    }

    /**
     * 删除房产的公共流程（逻辑删除）。
     *
     * <p>如果删除的是默认房产，则从该业主剩余房产中取创建时间最新的一套自动设为
     * 默认，保证业主始终有可用的默认地址；删除不会级联清理历史工单。</p>
     */
    private void doDelete(Property property) {
        // 逻辑删除，历史工单中的报修地址仍可追溯
        propertyMapper.deleteById(property.getId());
        // 删除的是默认房产时才需要补位
        if (Integer.valueOf(DEFAULT_YES).equals(property.getIsDefault())) {
            // 默认房产被删除后，自动把最新的一套顶上来
            List<Property> rest = propertyMapper.selectList(Wrappers.<Property>lambdaQuery()
                    .eq(Property::getOwnerId, property.getOwnerId())
                    .orderByDesc(Property::getCreateTime));
            // 仍有剩余房产才需要补位
            if (!rest.isEmpty()) {
                // 取剩余房产中最新登记的一套作为新默认
                Property next = new Property();
                // 指定补位房产主键
                next.setId(rest.get(0).getId());
                // 打开默认标记
                next.setIsDefault(DEFAULT_YES);
                // 只更新默认标记，避免覆盖其它字段
                propertyMapper.updateById(next);
            }
        }
        // 记录删除日志
        log.info("删除房产：propertyId={}", property.getId());
    }

    /**
     * 清除同一业主其它房产的默认标记，保证只保留 keepId 这一套默认房产。
     *
     * @param ownerId 业主用户 ID
     * @param keepId  需要保留默认标记的房产 ID
     */
    private void clearOtherDefault(Long ownerId, Long keepId) {
        // 一条 update 把所有默认标记中非 keepId 的房产清零
        propertyMapper.update(null, Wrappers.<Property>lambdaUpdate()
                .eq(Property::getOwnerId, ownerId)
                .eq(Property::getIsDefault, DEFAULT_YES)
                .ne(Property::getId, keepId)
                .set(Property::getIsDefault, DEFAULT_NO));
    }

    /** 插入房产，并把唯一索引冲突转换为“该房产已被登记”的业务异常 */
    private void insert(Property property) {
        // 插入落库，地址唯一索引兜底并发场景下的重复登记
        try {
            // 插入房产记录
            propertyMapper.insert(property);
        } catch (DuplicateKeyException e) {
            // 该地址已被登记，转为 CONFLICT 业务异常
            throw BizException.of(ResultCode.CONFLICT, "该房产已被登记");
        }
    }

    /**
     * 拆分字段落库，并拼接完整地址：小区 + 栋 + 单元 + 房号。
     *
     * <p>full_address 是冗余展示字段，用于列表与工单快照，避免每次查询再拼接；
     * 因此新增和修改房产时都必须经过本方法，保证拆分字段与完整地址同步更新。</p>
     */
    private void fillAddress(Property property, PropertySaveRequestVO requestVO) {
        // 拆分维度分别落库，便于后续按小区/栋单元检索
        property.setCommunity(requestVO.getCommunity());
        // 回填楼栋
        property.setBuilding(requestVO.getBuilding());
        // 回填单元
        property.setUnit(requestVO.getUnit());
        // 回填房号
        property.setRoom(requestVO.getRoom());
        // 冗余拼接完整地址，供列表展示与工单快照使用
        property.setFullAddress(requestVO.getCommunity() + requestVO.getBuilding() + "栋"
                + requestVO.getUnit() + "单元" + requestVO.getRoom());
    }

    /**
     * 校验房产属于当前登录业主，防止业主通过篡改 ID 操作他人房产。
     *
     * @param id 房产 ID
     * @return 房产实体
     * @throws BizException 房产不存在或归属不匹配时抛出
     */
    private Property requireOwned(Long id) {
        // 先确认房产存在
        Property property = requireExists(id);
        // 校验归属是否与当前登录业主一致
        if (!property.getOwnerId().equals(SecurityUtils.currentUserId())) {
            // 防止业主篡改 ID 操作他人房产
            throw BizException.of(ResultCode.PROPERTY_NOT_OWNED);
        }
        // 校验通过，返回房产实体
        return property;
    }

    /** 按 ID 查询房产，不存在时统一抛出 NOT_FOUND */
    private Property requireExists(Long id) {
        // 按主键查询房产
        Property property = propertyMapper.selectById(id);
        // 查询结果为空说明房产不存在
        if (property == null) {
            // 不存在时统一抛 NOT_FOUND
            throw BizException.of(ResultCode.NOT_FOUND, "房产不存在");
        }
        // 校验通过，返回房产实体
        return property;
    }

    /**
     * 归属人必须是业主角色账号。
     *
     * <p>按用户当前绑定的 roleId 查询角色编码判断，避免硬编码角色 ID。
     * 这里只校验角色，不校验账号启停状态，停用业主仍可作为房产归属人。</p>
     */
    private void requireOwnerUser(Long ownerId) {
        // 按 ID 查询归属人账号
        User user = userMapper.selectById(ownerId);
        // 账号缺失说明归属人无效
        if (user == null) {
            // 账号不存在时直接返回 404
            throw BizException.of(ResultCode.NOT_FOUND, "业主账号不存在");
        }
        // 按用户绑定的 roleId 反查角色，避免硬编码角色 ID
        Role role = roleMapper.selectById(user.getRoleId());
        // 角色不存在或非业主都判定为不合法
        if (role == null || !RoleEnum.OWNER.getCode().equals(role.getRoleCode())) {
            // 角色不是业主则拒绝登记房产
            throw BizException.of(ResultCode.PARAM_ERROR, "该账号不是业主，无法登记房产");
        }
    }

    private PropertyResponseVO toVO(Property property) {
        // 实体字段装配为前端展示对象
        return PropertyResponseVO.builder()
                .id(property.getId())
                .fullAddress(property.getFullAddress())
                .community(property.getCommunity())
                .building(property.getBuilding())
                .unit(property.getUnit())
                .room(property.getRoom())
                .areaSize(property.getAreaSize())
                .isDefault(property.getIsDefault())
                .status(property.getStatus())
                .build();
    }
}