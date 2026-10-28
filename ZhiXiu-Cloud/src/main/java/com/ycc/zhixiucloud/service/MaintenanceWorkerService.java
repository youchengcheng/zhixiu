package com.ycc.zhixiucloud.service;

import com.ycc.zhixiucloud.common.result.PageResult;
import com.ycc.zhixiucloud.entity.MaintenanceWorker;
import com.ycc.zhixiucloud.request.WorkerAuditRequestVO;
import com.ycc.zhixiucloud.request.WorkerQueryRequestVO;
import com.ycc.zhixiucloud.response.WorkerDetailResponseVO;
import com.ycc.zhixiucloud.response.WorkerListResponseVO;
import com.ycc.zhixiucloud.response.WorkerLoadResponseVO;
import com.ycc.zhixiucloud.response.WorkerStatisticsResponseVO;

import java.util.List;

/**
 * 师傅服务：审核、启停、负载、个人统计与评分回写。
 *
 * <p>职责边界：维护 maintenance_worker 档案及其派生指标（负载、评分、榜单），
 * 登录凭证与审核状态校验入口由 {@link AuthService} 负责。</p>
 *
 * <p>主要使用方：管理端师傅管理页面、师傅端个人中心/上线开关、派单服务。</p>
 *
 * <p>关键实现要点：</p>
 * <ul>
 *   <li>审核通过后工作状态置为离线，由师傅自行上线；驳回保留档案便于申诉；</li>
 *   <li>管理员启停与师傅上下线都会同步 Redis 在线心跳；</li>
 *   <li>评分回写同时更新 MySQL 平均分与月度满意度榜；</li>
 *   <li>依赖当前登录人的方法会校验师傅审核状态与被禁用状态。</li>
 * </ul>
 */
public interface MaintenanceWorkerService {

    /**
     * 管理端师傅分页列表。
     *
     * <p>支持审核状态、工作状态筛选及关键字（工号/姓名/手机号）模糊查询，
     * 结果批量补充用户姓名与手机号，并按创建时间倒序。</p>
     *
     * @param requestVO 分页与筛选参数
     * @return 师傅列表分页结果（含审核/工作状态文案）
     */
    PageResult<WorkerListResponseVO> page(WorkerQueryRequestVO requestVO);

    /**
     * 管理端师傅详情。
     *
     * <p>身份证号会脱敏为“前 6 位 + ******** + 后 4 位”后返回。</p>
     *
     * @param id 师傅 ID（maintenance_worker.id）
     * @return 师傅详情
     * @throws com.ycc.zhixiucloud.common.exception.BizException 师傅不存在时抛出 404
     */
    WorkerDetailResponseVO getDetail(Long id);

    /**
     * 审核师傅。
     *
     * <p>auditStatus：1 通过、2 驳回；通过时工作状态置为离线，等待师傅主动上线。
     * 驳回仅记录审核状态与备注，不删除档案，便于后续申诉或重新审核。</p>
     *
     * @param id        师傅 ID
     * @param requestVO 审核结果（auditStatus、auditRemark）
     * @throws com.ycc.zhixiucloud.common.exception.BizException 师傅不存在时抛出 404
     */
    void audit(Long id, WorkerAuditRequestVO requestVO);

    /**
     * 管理端启用/禁用师傅。
     *
     * <p>只接受 1 在线 / 4 禁用；更新数据库后同步 Redis 在线心跳：
     * 禁用删除心跳，上线写入心跳。</p>
     *
     * @param id         师傅 ID
     * @param workStatus 目标状态：1 在线、4 禁用
     * @throws com.ycc.zhixiucloud.common.exception.BizException 状态取值非法（400）
     *         或师傅不存在（404）
     */
    void updateWorkStatus(Long id, Integer workStatus);

    /**
     * 查询审核通过师傅的实时负载列表，供管理员派单时参考。
     *
     * <p>按当前负载降序、ID 升序返回；只读，不修改数据。</p>
     *
     * @return 师傅负载列表（含姓名、技能、工作状态、负载与平均分）
     */
    List<WorkerLoadResponseVO> listLoad();

    /**
     * 师傅个人统计。
     *
     * <p>统计接单总数、完工数、完工率、平均处理时长、平均评分与当前负载；
     * 完工率保留 3 位小数，无数据时返回 0。</p>
     *
     * @param workerId 师傅 ID
     * @return 个人统计结果
     * @throws com.ycc.zhixiucloud.common.exception.BizException 师傅不存在时抛出 404
     */
    WorkerStatisticsResponseVO statistics(Long workerId);

    /**
     * 师傅切换在线状态。
     *
     * <p>只接受 1 在线 / 2 离线，操作对象为当前登录师傅；上线写心跳，下线删心跳。
     * 在线状态只用于派单参考，真值仍以数据库 work_status 为准。</p>
     *
     * @param workStatus 目标状态：1 在线、2 离线
     * @throws com.ycc.zhixiucloud.common.exception.BizException 状态取值非法（400）
     *         或师傅未审核/被禁用
     */
    void switchWorkStatus(Integer workStatus);

    /**
     * 获取当前登录师傅档案，供需要“师傅身份”的业务复用。
     *
     * <p>从安全上下文取师傅 ID，校验档案存在、审核通过且未被禁用。</p>
     *
     * @return 当前师傅实体
     * @throws org.springframework.security.access.AccessDeniedException 当前账号不是维修师傅
     * @throws com.ycc.zhixiucloud.common.exception.BizException 档案不存在（404）、
     *         未通过审核（1005）或账号被禁用（1004）
     */
    MaintenanceWorker requireCurrentWorker();

    /**
     * 按评价表重算师傅平均分，并写入月度满意度榜。
     *
     * <p>平均分保留 1 位小数；无评价时记为 0。数据库更新后同步写入
     * Redis ZSet（rank:worker:score:{yyyyMM}），榜单保留 60 天。
     * 由 {@link EvaluationService#submit} 在评价事务内调用。</p>
     *
     * @param workerId 师傅 ID
     */
    void refreshScore(Long workerId);
}