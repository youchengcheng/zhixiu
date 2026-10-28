package com.ycc.zhixiucloud.service;

import com.ycc.zhixiucloud.common.result.PageResult;
import com.ycc.zhixiucloud.request.OwnerQueryRequestVO;
import com.ycc.zhixiucloud.response.OwnerListResponseVO;
import com.ycc.zhixiucloud.response.PropertyResponseVO;

import java.util.List;

/**
 * 业主管理服务（管理端）：业主列表、账号启停与名下房产查询。
 *
 * <p>职责边界：业主本质是角色为 OWNER 的 user 账号，本服务只做管理端查询与状态调整，
 * 注册与登录由 {@link AuthService} 负责，房产明细委托 {@link PropertyService}。</p>
 *
 * <p>主要使用方：管理端业主管理页面。</p>
 */
public interface OwnerService {

    /**
     * 管理端业主分页列表。
     *
     * <p>仅查询 OWNER 角色用户，支持账号状态与关键字（姓名/手机号/登录名）筛选；
     * 当前页业主的房产数量会批量统计后一并返回。</p>
     *
     * @param requestVO 分页与筛选参数
     * @return 业主分页结果（含房产数量、最后登录时间）
     */
    PageResult<OwnerListResponseVO> page(OwnerQueryRequestVO requestVO);

    /**
     * 启用/禁用业主账号。
     *
     * <p>禁用后该账号无法登录；已签发令牌在刷新时会重新校验账号状态。
     * 仅允许操作 OWNER 角色账号。</p>
     *
     * @param id     用户 ID
     * @param status 目标状态：1 正常、0 禁用
     * @throws com.ycc.zhixiucloud.common.exception.BizException 状态取值非法（400）、
     *         账号不存在（404）或目标账号不是业主（400）
     */
    void updateStatus(Long id, Integer status);

    /**
     * 查询指定业主名下的房产。
     *
     * <p>先校验账号存在且为业主角色，再委托 {@link PropertyService#listByOwner}。</p>
     *
     * @param ownerId 业主用户 ID
     * @return 房产列表；无房产时返回空列表
     * @throws com.ycc.zhixiucloud.common.exception.BizException 账号不存在（404）
     *         或不是业主（400）
     */
    List<PropertyResponseVO> listProperties(Long ownerId);
}