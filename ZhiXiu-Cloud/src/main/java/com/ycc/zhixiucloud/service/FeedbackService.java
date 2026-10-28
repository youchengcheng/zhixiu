package com.ycc.zhixiucloud.service;

import com.ycc.zhixiucloud.common.result.PageResult;
import com.ycc.zhixiucloud.request.FeedbackCreateRequestVO;
import com.ycc.zhixiucloud.request.FeedbackQueryRequestVO;
import com.ycc.zhixiucloud.request.FeedbackReplyRequestVO;
import com.ycc.zhixiucloud.request.PageRequestVO;
import com.ycc.zhixiucloud.response.FeedbackResponseVO;

/**
 * 问题反馈服务：业主/师傅提交与查看自己的反馈，管理员查看全部并回复。
 *
 * <p>职责边界：只处理反馈单本身，不联动工单流程；提交人与角色全部取自登录态，
 * 前端无法伪造。列表中的提交人/回复人姓名是批量补充的展示字段，不是表字段。</p>
 *
 * <p>主要使用方：业主/师傅端“问题反馈”页面、管理端反馈处理页面。</p>
 *
 * <p>关键实现要点：新建反馈固定为待处理；回复时写入回复人与回复时间，
 * 未指定状态时默认置为处理中；写操作使用数据库事务。</p>
 */
public interface FeedbackService {

    /**
     * 提交反馈。
     *
     * <p>提交人 ID 与角色取自当前登录态，初始状态为待处理（0）；
     * 联系方式为选填字段，空白会统一置为 null。</p>
     *
     * @param requestVO 反馈内容（类型、标题、内容、联系方式）
     * @return 新反馈 ID
     */
    Long create(FeedbackCreateRequestVO requestVO);

    /**
     * 查询当前登录用户提交的反馈，按提交时间倒序。
     *
     * @param requestVO 分页参数
     * @return 反馈分页结果（含提交人/回复人姓名）
     */
    PageResult<FeedbackResponseVO> pageMine(PageRequestVO requestVO);

    /**
     * 管理端反馈列表。
     *
     * <p>支持按状态、类型筛选，未传则不限制；按提交时间倒序。</p>
     *
     * @param requestVO 分页与筛选参数（status、type）
     * @return 全部用户反馈的分页结果
     */
    PageResult<FeedbackResponseVO> pageForAdmin(FeedbackQueryRequestVO requestVO);

    /**
     * 回复反馈。
     *
     * <p>写入回复内容、回复人（当前管理员）与回复时间；status 为空时默认置为处理中（1），
     * 传入时必须为 1 处理中 / 2 已处理 / 3 关闭。</p>
     *
     * @param id        反馈 ID
     * @param requestVO 回复内容与可选的目标状态
     * @throws com.ycc.zhixiucloud.common.exception.BizException 状态取值非法（400）
     *         或反馈不存在（404）
     */
    void reply(Long id, FeedbackReplyRequestVO requestVO);
}
