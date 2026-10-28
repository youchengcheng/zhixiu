package com.ycc.zhixiucloud.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ycc.zhixiucloud.common.enums.RoleEnum;
import com.ycc.zhixiucloud.common.exception.BizException;
import com.ycc.zhixiucloud.common.result.PageResult;
import com.ycc.zhixiucloud.common.result.ResultCode;
import com.ycc.zhixiucloud.common.util.SecurityUtils;
import com.ycc.zhixiucloud.entity.Announcement;
import com.ycc.zhixiucloud.mapper.AnnouncementMapper;
import com.ycc.zhixiucloud.mapper.UserMapper;
import com.ycc.zhixiucloud.request.AnnouncementSaveRequestVO;
import com.ycc.zhixiucloud.request.PageRequestVO;
import com.ycc.zhixiucloud.response.AnnouncementResponseVO;
import com.ycc.zhixiucloud.service.AnnouncementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 公告服务实现。
 *
 * <p>职责：公告分页查询、详情阅读量累加、增删改以及发布/下架状态切换。</p>
 *
 * <p>可见范围：管理员可查看全部公告（含草稿）；业主/师傅只能看到「已发布」且投放范围为
 * ALL 或本人角色的公告。列表置顶优先、发布时间倒序。</p>
 *
 * <p>状态流转：新建一律为草稿（0），publish 只接受 0/1；首次发布写入发布时间，
 * 之后下架/重新发布保留原发布时间以保证排序稳定。当前为直接读库实现，未引入缓存或定时发布，
 * 状态切换由管理员手动触发。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementServiceImpl implements AnnouncementService {

    /** 状态：草稿 */
    private static final int STATUS_DRAFT = 0;

    /** 状态：已发布 */
    private static final int STATUS_PUBLISHED = 1;

    /** 投放范围：全部用户 */
    private static final String TARGET_ALL = "ALL";

    /** 默认类型 */
    private static final String DEFAULT_TYPE = "SYSTEM";

    private final AnnouncementMapper announcementMapper;

    private final UserMapper userMapper;

    /**
     * 分页查询公告。
     *
     * <p>按角色拼接可见范围条件：非管理员强制「已发布」且投放范围为 ALL 或本人角色；
     * 管理员不加过滤，可看到草稿。置顶优先，其次按发布时间倒序。</p>
     *
     * @param requestVO 分页参数
     * @param roleCode  当前用户角色编码
     * @return 统一分页结果，发布人姓名已批量填充
     */
    @Override
    public PageResult<AnnouncementResponseVO> page(PageRequestVO requestVO, String roleCode) {
        // 判断当前角色是否管理员
        boolean admin = RoleEnum.ADMIN.getCode().equals(roleCode);
        // 按分页参数与角色过滤条件查询
        Page<Announcement> page = announcementMapper.selectPage(
                // 分页参数：页码与每页条数
                new Page<>(requestVO.getPageNum(), requestVO.getPageSize()),
                // 构造公告查询条件
                Wrappers.<Announcement>lambdaQuery()
                        // 管理员看全部（含草稿）；业主/师傅只看已发布且投放范围命中自己的
                        .eq(!admin, Announcement::getStatus, STATUS_PUBLISHED)
                        // 非管理员限定投放范围为全部或本人角色
                        .and(!admin, wrapper -> wrapper
                                .eq(Announcement::getTargetRole, TARGET_ALL)
                                .or()
                                .eq(Announcement::getTargetRole, roleCode))
                        // 置顶优先
                        .orderByDesc(Announcement::getIsTop)
                        // 其次按发布时间倒序
                        .orderByDesc(Announcement::getPublishTime));

        // 批量查询发布人姓名，避免逐条查库
        Map<Long, String> publisherNames = loadPublisherNames(page.getRecords());
        // 构造出参分页对象，沿用总记录数
        Page<AnnouncementResponseVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        // 逐条实体转换为出参 VO
        voPage.setRecords(page.getRecords().stream()
                .map(item -> toVO(item, publisherNames))
                .toList());
        // 包装为统一分页结果
        return PageResult.of(voPage);
    }

    /**
     * 查询公告详情并累加阅读量。
     *
     * <p>阅读量采用「读当前值 + 1 后写回」，并用只带主键与 viewCount 的局部实体更新，
     * 避免全字段覆盖；并发访问时计数为近似值。</p>
     *
     * @param id 公告 ID
     * @return 公告详情（含最新阅读量与发布人姓名）
     * @throws BizException 公告不存在（NOT_FOUND）时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AnnouncementResponseVO detail(Long id) {
        // 查询公告，不存在则抛异常
        Announcement entity = requireAnnouncement(id);
        // 阅读量空值按 0 处理后再累加一
        int nextViewCount = (entity.getViewCount() == null ? 0 : entity.getViewCount()) + 1;
        // 构造仅用于更新阅读量的实体
        Announcement viewUpdate = new Announcement();
        // 指定主键，避免全字段更新
        viewUpdate.setId(entity.getId());
        // 写入累加后的阅读量
        viewUpdate.setViewCount(nextViewCount);
        // 持久化阅读量
        announcementMapper.updateById(viewUpdate);
        // 回填实体，使出参阅读量最新
        entity.setViewCount(nextViewCount);
        // 组装出参并补充发布人姓名
        return toVO(entity, loadPublisherNames(List.of(entity)));
    }

    /**
     * 新建公告（草稿）。
     *
     * <p>标题去除首尾空格；类型、投放范围、置顶提供默认值；发布人取当前登录用户。
     * 新建不直接发布，需再调用 publish 切换状态。</p>
     *
     * @param requestVO 公告内容
     * @return 新公告 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(AnnouncementSaveRequestVO requestVO) {
        // 组装待插入实体
        Announcement entity = new Announcement();
        // 标题去除首尾空格
        entity.setTitle(requestVO.getTitle().trim());
        // 内容原样保存
        entity.setContent(requestVO.getContent());
        // 类型缺省为系统通知
        entity.setType(requestVO.getType() == null ? DEFAULT_TYPE : requestVO.getType());
        // 投放范围缺省为全部用户
        entity.setTargetRole(requestVO.getTargetRole() == null ? TARGET_ALL : requestVO.getTargetRole());
        // 发布人取当前登录用户
        entity.setPublisherId(SecurityUtils.currentUserId());
        // 置顶缺省为否
        entity.setIsTop(requestVO.getIsTop() == null ? 0 : requestVO.getIsTop());
        // 新建即草稿态
        entity.setStatus(STATUS_DRAFT);
        // 初始阅读量为 0
        entity.setViewCount(0);
        // 插入数据库并回填主键
        announcementMapper.insert(entity);
        // 记录新增日志
        log.info("新增公告草稿：id={}, title={}, targetRole={}",
                entity.getId(), entity.getTitle(), entity.getTargetRole());
        // 返回新公告主键
        return entity.getId();
    }

    /**
     * 修改公告。
     *
     * <p>仅更新请求中显式传入的字段（类型/投放范围/置顶），避免把未传字段覆盖为 null；
     * 状态与发布时间不在此接口修改。</p>
     *
     * @param id        公告 ID
     * @param requestVO 待更新内容
     * @throws BizException 公告不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, AnnouncementSaveRequestVO requestVO) {
        // 先校验公告存在
        Announcement exists = requireAnnouncement(id);
        // 组装待更新实体
        Announcement entity = new Announcement();
        // 指定主键
        entity.setId(exists.getId());
        // 标题去除首尾空格
        entity.setTitle(requestVO.getTitle().trim());
        // 更新内容
        entity.setContent(requestVO.getContent());
        // 未传的字段保持原值
        // 类型传了才更新
        if (requestVO.getType() != null) {
            // 覆盖类型字段
            entity.setType(requestVO.getType());
        }
        // 投放范围传了才更新
        if (requestVO.getTargetRole() != null) {
            // 覆盖投放范围字段
            entity.setTargetRole(requestVO.getTargetRole());
        }
        // 置顶传了才更新
        if (requestVO.getIsTop() != null) {
            // 覆盖置顶标记
            entity.setIsTop(requestVO.getIsTop());
        }
        // 按主键更新非空字段
        announcementMapper.updateById(entity);
        // 记录修改日志
        log.info("修改公告：id={}", id);
    }

    /**
     * 删除公告。
     *
     * @param id 公告 ID
     * @throws BizException 公告不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 先校验公告存在
        requireAnnouncement(id);
        // 按主键删除
        announcementMapper.deleteById(id);
        // 记录删除日志
        log.info("删除公告：id={}", id);
    }

    /**
     * 发布或下架公告。
     *
     * <p>status 仅允许 0（草稿/下架）或 1（发布）。首次发布时补充 publishTime；
     * 下架时不清空发布时间，重新发布也不再刷新，保证列表按首次发布时间稳定排序。</p>
     *
     * @param id     公告 ID
     * @param status 目标状态：0 草稿，1 已发布
     * @throws BizException 状态非法（PARAM_ERROR）或公告不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id, Integer status) {
        // 校验状态只能是草稿或发布
        if (status == null || (status != STATUS_DRAFT && status != STATUS_PUBLISHED)) {
            // 非法取值直接拒绝
            throw BizException.of(ResultCode.PARAM_ERROR, "发布状态取值 0/1");
        }
        // 校验公告存在
        Announcement exists = requireAnnouncement(id);

        // 组装待更新实体
        Announcement entity = new Announcement();
        // 指定主键
        entity.setId(exists.getId());
        // 写入目标状态
        entity.setStatus(status);
        // 首次发布记录发布时间；下架时保留原发布时间，便于列表排序稳定
        // 仅首次发布时补发布时间
        if (status == STATUS_PUBLISHED && exists.getPublishTime() == null) {
            // 首次发布才写入发布时间
            entity.setPublishTime(LocalDateTime.now());
        }
        // 持久化状态变更
        announcementMapper.updateById(entity);
        // 记录状态调整日志
        log.info("公告状态调整：id={}, status={}", id, status);
    }

    /**
     * 批量加载发布人姓名。
     *
     * <p>发布人姓名是展示用关联字段，不属于公告表；先收集去重后的发布人 ID，
     * 再一次性批量查用户，避免列表逐条查询造成 N+1。</p>
     *
     * @param list 公告列表
     * @return 发布人 ID 到姓名的映射；无发布人时返回空 Map
     */
    private Map<Long, String> loadPublisherNames(List<Announcement> list) {
        // 收集去重后的非空发布人 ID
        Set<Long> publisherIds = list.stream()
                .map(Announcement::getPublisherId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // 无发布人时直接返回空映射
        if (publisherIds.isEmpty()) {
            // 无发布人 ID 返回空映射，避免非法查询
            return Collections.emptyMap();
        }
        // 按数量预分配容量的姓名映射
        Map<Long, String> names = new HashMap<>(publisherIds.size());
        // 批量查用户，填充 ID 到姓名的映射
        userMapper.selectBatchIds(publisherIds).forEach(user -> names.put(user.getId(), user.getRealName()));
        // 返回映射供装配出参
        return names;
    }

    /**
     * 按主键查询公告，不存在则抛业务异常。
     *
     * @param id 公告 ID
     * @return 公告实体
     * @throws BizException 公告不存在（NOT_FOUND）时抛出
     */
    private Announcement requireAnnouncement(Long id) {
        // 按主键查询公告
        Announcement entity = announcementMapper.selectById(id);
        // 不存在则报错
        if (entity == null) {
            // 公告不存在统一抛 404
            throw BizException.of(ResultCode.NOT_FOUND, "公告不存在");
        }
        // 返回查到的实体
        return entity;
    }

    /**
     * 公告实体转出参 VO。
     *
     * @param entity         公告实体
     * @param publisherNames 发布人 ID 到姓名的映射
     * @return 公告出参
     */
    private AnnouncementResponseVO toVO(Announcement entity, Map<Long, String> publisherNames) {
        // 通过 Builder 逐字段装配出参
        return AnnouncementResponseVO.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .content(entity.getContent())
                .type(entity.getType())
                .targetRole(entity.getTargetRole())
                .publisherId(entity.getPublisherId())
                // 姓名取自批量查询映射
                .publisherName(publisherNames.get(entity.getPublisherId()))
                .publishTime(entity.getPublishTime())
                .isTop(entity.getIsTop())
                .status(entity.getStatus())
                .viewCount(entity.getViewCount())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                // 构建出参对象
                .build();
    }
}
