package com.ycc.zhixiucloud.service;

import com.ycc.zhixiucloud.request.SlaConfigRequestVO;
import com.ycc.zhixiucloud.response.SlaConfigResponseVO;

import java.util.List;

/**
 * SLA 配置服务（管理端）：SLA 规则的增删改查与启停。
 *
 * <p>职责边界：只维护规则数据，规则匹配、超时点计算与超时升级由 {@link SlaService} 完成。</p>
 *
 * <p>主要使用方：管理端 SLA 规则配置页面。</p>
 *
 * <p>关键实现要点：规则按 category_id + urgency + safety_flag + affect_scope 四维匹配；
 * 四个维度全空的规则是全局兜底规则，必须保留且不可删除；新增规则默认启用。</p>
 */
public interface SlaConfigService {

    /**
     * 查询全部 SLA 规则。
     *
     * @return 规则列表，按派单权重降序、ID 升序
     */
    List<SlaConfigResponseVO> list();

    /**
     * 新增 SLA 规则。
     *
     * <p>新规则默认启用；priorityWeight 为空按 0 处理。</p>
     *
     * @param requestVO 规则内容（匹配维度、响应/处理时限、升级对象、权重）
     * @return 新规则 ID
     */
    Long create(SlaConfigRequestVO requestVO);

    /**
     * 修改 SLA 规则。
     *
     * <p>允许调整匹配维度、时限与权重；不修改启停状态。</p>
     *
     * @param id        规则 ID
     * @param requestVO 修改内容
     * @throws com.ycc.zhixiucloud.common.exception.BizException 规则不存在时抛出 404
     */
    void update(Long id, SlaConfigRequestVO requestVO);

    /**
     * 启用/禁用 SLA 规则。
     *
     * <p>禁用的规则不参与 {@link SlaService#matchSla} 匹配。</p>
     *
     * @param id      规则 ID
     * @param enabled 目标状态：1 启用、0 禁用
     * @throws com.ycc.zhixiucloud.common.exception.BizException 规则不存在（404）
     *         或状态取值非法（400）
     */
    void updateEnabled(Long id, Integer enabled);

    /**
     * 删除 SLA 规则。
     *
     * <p>全局兜底规则（四个匹配维度全空）不允许删除。</p>
     *
     * @param id 规则 ID
     * @throws com.ycc.zhixiucloud.common.exception.BizException 规则不存在（404）
     *         或为全局兜底规则（400）
     */
    void delete(Long id);
}