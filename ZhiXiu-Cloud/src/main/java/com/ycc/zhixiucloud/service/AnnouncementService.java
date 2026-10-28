package com.ycc.zhixiucloud.service;

import com.ycc.zhixiucloud.common.result.PageResult;
import com.ycc.zhixiucloud.request.AnnouncementSaveRequestVO;
import com.ycc.zhixiucloud.request.PageRequestVO;
import com.ycc.zhixiucloud.response.AnnouncementResponseVO;

/**
 * 公告服务：公告的创建、修改、发布/下架、查询与阅读量统计。
 *
 * <p>职责边界：读侧按角色过滤投放范围（ALL / OWNER / WORKER / ADMIN），
 * 写侧仅管理员可用（控制器层已完成角色鉴权），本接口不负责消息推送。</p>
 *
 * <p>主要使用方：管理端公告维护页面、业主/师傅端公告列表与详情页。</p>
 *
 * <p>关键实现要点：</p>
 * <ul>
 *   <li>新建公告一律为草稿，需再调用 {@link #publish} 上线；</li>
 *   <li>写操作使用数据库事务；发布人取自登录态，发布人姓名在查询时批量补充；</li>
 *   <li>阅读量在查询详情时自增，因此详情接口有写副作用；</li>
 *   <li>列表按置顶优先、发布时间倒序排列。</li>
 * </ul>
 */
public interface AnnouncementService {

    /**
     * 公告分页查询。
     *
     * <p>管理员可见全部公告（含草稿）；业主/师傅仅可见已发布，且投放范围为 ALL
     * 或与自身角色编码一致的公告。结果按置顶、发布时间倒序排列。</p>
     *
     * @param requestVO 分页参数
     * @param roleCode  当前登录角色编码，用于决定可见范围
     * @return 公告分页结果（含发布人姓名）
     */
    PageResult<AnnouncementResponseVO> page(PageRequestVO requestVO, String roleCode);

    /**
     * 查询公告详情，并在同一事务内将阅读量加 1。
     *
     * @param id 公告 ID
     * @return 公告详情（阅读量为本次累加后的值）
     * @throws com.ycc.zhixiucloud.common.exception.BizException 公告不存在时抛出 404
     */
    AnnouncementResponseVO detail(Long id);

    /**
     * 新增公告草稿。
     *
     * <p>发布人取当前登录用户；type 为空默认 SYSTEM，targetRole 为空默认 ALL，
     * isTop 为空默认不置顶；新建后状态固定为草稿，需调用 {@link #publish} 发布。</p>
     *
     * @param requestVO 公告内容（标题、正文、类型、投放范围、是否置顶）
     * @return 新公告 ID
     */
    Long create(AnnouncementSaveRequestVO requestVO);

    /**
     * 修改公告。
     *
     * <p>标题与内容必填；type/targetRole/isTop 仅在入参非空时更新，其余字段保持原值，
     * 不会改变公告的发布状态。</p>
     *
     * @param id        公告 ID
     * @param requestVO 待修改内容
     * @throws com.ycc.zhixiucloud.common.exception.BizException 公告不存在时抛出 404
     */
    void update(Long id, AnnouncementSaveRequestVO requestVO);

    /**
     * 删除公告（物理删除）。
     *
     * @param id 公告 ID
     * @throws com.ycc.zhixiucloud.common.exception.BizException 公告不存在时抛出 404
     */
    void delete(Long id);

    /**
     * 发布或下架公告。
     *
     * <p>首次发布（原 publishTime 为空）时记录发布时间；下架（status=0）保留原发布时间，
     * 便于再次发布后列表排序稳定。</p>
     *
     * @param id     公告 ID
     * @param status 目标状态：0 草稿（下架）、1 已发布
     * @throws com.ycc.zhixiucloud.common.exception.BizException 状态取值非法（400）
     *         或公告不存在（404）
     */
    void publish(Long id, Integer status);
}
