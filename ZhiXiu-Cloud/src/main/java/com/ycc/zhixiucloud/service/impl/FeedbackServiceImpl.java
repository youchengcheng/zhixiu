package com.ycc.zhixiucloud.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ycc.zhixiucloud.common.exception.BizException;
import com.ycc.zhixiucloud.common.result.PageResult;
import com.ycc.zhixiucloud.common.result.ResultCode;
import com.ycc.zhixiucloud.common.util.SecurityUtils;
import com.ycc.zhixiucloud.entity.Feedback;
import com.ycc.zhixiucloud.mapper.FeedbackMapper;
import com.ycc.zhixiucloud.mapper.UserMapper;
import com.ycc.zhixiucloud.request.FeedbackCreateRequestVO;
import com.ycc.zhixiucloud.request.FeedbackQueryRequestVO;
import com.ycc.zhixiucloud.request.FeedbackReplyRequestVO;
import com.ycc.zhixiucloud.request.PageRequestVO;
import com.ycc.zhixiucloud.response.FeedbackResponseVO;
import com.ycc.zhixiucloud.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 问题反馈服务实现：业主/师傅提交与查看自己的反馈，管理员查看全部并回复。
 *
 * <p>状态取值：0 待处理（新建默认）；回复时可指定 1 处理中（未指定时默认）/ 2 已处理 /
 * 3 已关闭。当前实现未强制单向流转，管理员可再次回复并调整状态。</p>
 *
 * <p>关键设计：</p>
 * <ul>
 *   <li>提交人、提交人角色、回复人均取自登录态，不信任前端传入的身份字段。</li>
 *   <li>列表先分页查询反馈，再按本页涉及的提交人/回复人 ID 一次批量查询用户姓名，
 *       避免逐条关联查询造成 N+1。</li>
 *   <li>回复使用 {@code updateById} 只更新回复内容、回复人、回复时间与状态，
 *       未赋值的字段不会被覆盖。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    /** 状态：待处理 */
    private static final int STATUS_PENDING = 0;

    /** 状态：处理中 */
    private static final int STATUS_PROCESSING = 1;

    /** 允许回复时指定的状态：1处理中 2已处理 3关闭 */
    private static final Set<Integer> REPLY_STATUS = Set.of(1, 2, 3);

    // 反馈数据访问
    private final FeedbackMapper feedbackMapper;

    // 用户数据访问（取展示用姓名）
    private final UserMapper userMapper;

    /**
     * 提交反馈。
     *
     * <p>提交人与提交人角色取自登录态；标题去首尾空格，联系方式为空白时统一存 null；
     * 初始状态固定为「待处理」。</p>
     *
     * @param requestVO 反馈类型、标题、内容与可选联系方式
     * @return 新反馈主键
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(FeedbackCreateRequestVO requestVO) {
        // 新建反馈实体
        Feedback entity = new Feedback();
        // 提交人 ID 取自登录态
        entity.setUserId(SecurityUtils.currentUserId());
        // 提交人角色取自登录态
        entity.setUserRole(SecurityUtils.currentRoleCode());
        // 类型直接取入参
        entity.setType(requestVO.getType());
        // 标题去除首尾空格
        entity.setTitle(requestVO.getTitle().trim());
        // 内容原样保存
        entity.setContent(requestVO.getContent());
        // 联系方式选填：有值去空格，否则置空
        entity.setContact(StringUtils.hasText(requestVO.getContact()) ? requestVO.getContact().trim() : null);
        // 新提交默认「待处理」
        entity.setStatus(STATUS_PENDING);
        // 写入数据库
        feedbackMapper.insert(entity);
        // 记录提交日志，便于排查
        log.info("提交反馈：id={}, userId={}, type={}", entity.getId(), entity.getUserId(), entity.getType());
        // 返回新反馈主键
        return entity.getId();
    }

    /**
     * 分页查询当前登录用户提交的反馈。
     *
     * <p>按提交时间倒序；只返回本人记录，用户 ID 取自登录态，不接受前端指定。</p>
     *
     * @param requestVO 分页参数（页码、每页条数）
     * @return 统一分页结果，记录中已回填提交人/回复人姓名
     */
    @Override
    public PageResult<FeedbackResponseVO> pageMine(PageRequestVO requestVO) {
        // 当前登录用户 ID
        Long userId = SecurityUtils.currentUserId();
        // 按分页参数与本人 ID 查询本页反馈
        Page<Feedback> page = feedbackMapper.selectPage(
                // 按入参页码与每页条数分页
                new Page<>(requestVO.getPageNum(), requestVO.getPageSize()),
                // 仅按本人 ID 过滤，不做归属外查询
                Wrappers.<Feedback>lambdaQuery()
                        // 只查当前用户提交的反馈
                        .eq(Feedback::getUserId, userId)
                        // 按提交时间倒序
                        .orderByDesc(Feedback::getCreateTime));
        // 转换为出参分页
        return toPage(page);
    }

    /**
     * 分页查询全部反馈（管理端）。
     *
     * <p>状态、类型均为可选筛选条件，非空时才拼入 SQL；结果按提交时间倒序。</p>
     *
     * @param requestVO 分页参数与状态/类型筛选条件
     * @return 统一分页结果，记录中已回填提交人/回复人姓名
     */
    @Override
    public PageResult<FeedbackResponseVO> pageForAdmin(FeedbackQueryRequestVO requestVO) {
        // 按分页参数与筛选条件查询全部反馈
        Page<Feedback> page = feedbackMapper.selectPage(
                // 按入参页码与每页条数分页
                new Page<>(requestVO.getPageNum(), requestVO.getPageSize()),
                // 按可选条件组装查询
                Wrappers.<Feedback>lambdaQuery()
                        // 状态非空才作为筛选条件
                        .eq(requestVO.getStatus() != null, Feedback::getStatus, requestVO.getStatus())
                        // 类型有值才作为筛选条件
                        .eq(StringUtils.hasText(requestVO.getType()), Feedback::getType, requestVO.getType())
                        // 按提交时间倒序
                        .orderByDesc(Feedback::getCreateTime));
        // 转换为出参分页
        return toPage(page);
    }

    /**
     * 回复反馈并更新处理状态。
     *
     * <p>回复人取登录态，回复时间取当前时间；未显式指定状态时默认置为「处理中」，指定时
     * 仅允许 1 处理中 / 2 已处理 / 3 关闭。回复内容会去除首尾空格。</p>
     *
     * @param id        反馈主键
     * @param requestVO 回复内容与可选的处理状态
     * @throws BizException 反馈不存在时抛出 404；状态取值不在 1/2/3 时抛出参数错误
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reply(Long id, FeedbackReplyRequestVO requestVO) {
        // 入参指定的处理状态，可为空
        Integer status = requestVO.getStatus();
        // 指定了状态则校验取值是否合法
        if (status != null && !REPLY_STATUS.contains(status)) {
            // 非法状态抛出参数错误
            throw BizException.of(ResultCode.PARAM_ERROR, "处理状态取值 1/2/3");
        }
        // 校验反馈存在
        Feedback exists = requireFeedback(id);

        // 组装待更新实体
        Feedback entity = new Feedback();
        // 仅按主键更新，避免覆盖其它字段
        entity.setId(exists.getId());
        // 回复内容去除首尾空格
        entity.setReply(requestVO.getReply().trim());
        // 回复人取自登录态
        entity.setReplierId(SecurityUtils.currentUserId());
        // 记录回复时间
        entity.setReplyTime(LocalDateTime.now());
        // 未显式指定状态时，回复即视为「处理中」
        entity.setStatus(status == null ? STATUS_PROCESSING : status);
        // 执行更新
        feedbackMapper.updateById(entity);
        // 记录回复日志
        log.info("回复反馈：id={}, replierId={}, status={}", id, entity.getReplierId(), entity.getStatus());
    }

    /**
     * 查询反馈并校验存在。
     *
     * @param id 反馈主键
     * @return 反馈实体
     * @throws BizException 反馈不存在时抛出 404 业务异常
     */
    private Feedback requireFeedback(Long id) {
        // 按主键查询反馈
        Feedback entity = feedbackMapper.selectById(id);
        // 不存在则抛 404 业务异常
        if (entity == null) {
            // 反馈不存在统一抛 404
            throw BizException.of(ResultCode.NOT_FOUND, "反馈不存在");
        }
        // 存在则返回实体
        return entity;
    }

    /**
     * 分页实体转换为出参分页。
     *
     * <p>沿用原分页的当前页、每页条数与总记录数，只替换记录内容，并统一批量回填用户姓名。</p>
     *
     * @param page 反馈实体分页
     * @return 反馈 VO 分页结果
     */
    private PageResult<FeedbackResponseVO> toPage(Page<Feedback> page) {
        // 批量取本页提交人与回复人姓名
        Map<Long, String> names = loadNames(page.getRecords());
        // 复制分页元信息：当前页、每页条数、总记录数
        Page<FeedbackResponseVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        // 逐条转换为出参
        voPage.setRecords(page.getRecords().stream()
                // 组装单条出参（含关联姓名）
                .map(item -> toVO(item, names))
                .toList());
        // 包装为统一分页结果
        return PageResult.of(voPage);
    }

    /**
     * 批量取提交人与回复人姓名。
     *
     * <p>合并本页所有提交人、回复人 ID 后一次批量查询用户，避免逐条查询；姓名优先取真名，
     * 真名为空时回退用户名。</p>
     *
     * @param list 当前页反馈记录
     * @return 用户 ID 到展示姓名的映射
     */
    private Map<Long, String> loadNames(List<Feedback> list) {
        // 收集本页涉及的提交人与回复人 ID
        Set<Long> userIds = new HashSet<>();
        // 遍历本页记录收集涉及的用户 ID
        list.forEach(item -> {
            // 收集提交人 ID
            if (item.getUserId() != null) {
                // 加入待查询集合
                userIds.add(item.getUserId());
            }
            // 收集回复人 ID
            if (item.getReplierId() != null) {
                // 加入待查询集合
                userIds.add(item.getReplierId());
            }
        });
        // 无关联用户时返回空映射
        if (userIds.isEmpty()) {
            // 无用户 ID 直接返回空映射，避免非法查询
            return Collections.emptyMap();
        }
        // 按预计规模初始化映射
        Map<Long, String> names = new HashMap<>(userIds.size());
        // 批量查用户，建立 ID→姓名 映射（无真名回退用户名）
        userMapper.selectBatchIds(userIds)
                .forEach(user -> names.put(user.getId(), Objects.requireNonNullElse(user.getRealName(), user.getUsername())));
        // 返回姓名映射
        return names;
    }

    /**
     * 反馈实体转出参 VO。
     *
     * @param entity 反馈实体
     * @param names  用户 ID 到姓名的映射（提交人、回复人共用）
     * @return 带提交人/回复人姓名的反馈 VO
     */
    private FeedbackResponseVO toVO(Feedback entity, Map<Long, String> names) {
        // 组装反馈出参
        return FeedbackResponseVO.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                // 提交人姓名来自关联映射，可能为空
                .userName(names.get(entity.getUserId()))
                .userRole(entity.getUserRole())
                .type(entity.getType())
                .title(entity.getTitle())
                .content(entity.getContent())
                .contact(entity.getContact())
                .status(entity.getStatus())
                .reply(entity.getReply())
                .replierId(entity.getReplierId())
                // 回复人姓名来自关联映射，可能为空
                .replierName(names.get(entity.getReplierId()))
                .replyTime(entity.getReplyTime())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                // 构建反馈出参
                .build();
    }
}
