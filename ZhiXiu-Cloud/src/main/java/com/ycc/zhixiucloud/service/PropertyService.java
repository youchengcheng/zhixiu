package com.ycc.zhixiucloud.service;

import com.ycc.zhixiucloud.request.AdminPropertySaveRequestVO;
import com.ycc.zhixiucloud.request.PropertySaveRequestVO;
import com.ycc.zhixiucloud.response.PropertyResponseVO;

import java.util.List;

/**
 * 房产服务：业主自助维护 + 管理端代维护。
 *
 * <p>职责边界：只维护 property 表及默认房产规则，不校验房产是否已被工单引用。</p>
 *
 * <p>主要使用方：业主端“我的房产”页面、管理端业主房产维护页面。</p>
 *
 * <p>关键实现要点：</p>
 * <ul>
 *   <li>业主侧写操作必须校验房产归属（PROPERTY_NOT_OWNED）；</li>
 *   <li>同一业主仅一套默认房产，第一套自动设为默认；</li>
 *   <li>默认房产删除后自动把该业主最新创建的一套顶上来；</li>
 *   <li>新增/修改使用数据库事务，并发重复登记由唯一索引兜底。</li>
 * </ul>
 */
public interface PropertyService {

    /**
     * 查询当前登录业主的房产，默认房产排在最前，其次按创建时间倒序。
     *
     * @return 房产列表；无房产时返回空列表
     */
    List<PropertyResponseVO> listMine();

    /**
     * 业主新增房产。
     *
     * <p>归属人固定为当前登录业主；第一套房产自动设为默认，
     * 若显式指定 isDefault=1 则同时清除其它默认标记。</p>
     *
     * @param requestVO 房产信息（小区、楼栋、单元、房号、面积等）
     * @return 新房产 ID
     * @throws com.ycc.zhixiucloud.common.exception.BizException 房产重复登记时抛出 409
     */
    Long create(PropertySaveRequestVO requestVO);

    /**
     * 业主修改房产。
     *
     * <p>修改前校验房产归属当前登录业主；isDefault=1 时会把同业主其它房产的
     * 默认标记清除。</p>
     *
     * @param id        房产 ID
     * @param requestVO 修改内容
     * @throws com.ycc.zhixiucloud.common.exception.BizException 房产不存在（404）、
     *         不属于当前业主（2004）或修改后与已有房产重复（409）
     */
    void update(Long id, PropertySaveRequestVO requestVO);

    /**
     * 业主删除房产。
     *
     * <p>校验归属后删除；若删除的是默认房产，会自动把该业主最新创建的一套设为默认。</p>
     *
     * @param id 房产 ID
     * @throws com.ycc.zhixiucloud.common.exception.BizException 房产不存在（404）
     *         或不属于当前业主（2004）
     */
    void delete(Long id);

    /**
     * 设为默认房产。
     *
     * <p>校验归属后先清除该业主其它房产的默认标记，再设置当前房产，保证仅一套默认。</p>
     *
     * @param id 房产 ID
     * @throws com.ycc.zhixiucloud.common.exception.BizException 房产不存在（404）
     *         或不属于当前业主（2004）
     */
    void setDefault(Long id);

    /**
     * 管理端查询某业主名下房产。
     *
     * <p>只读，不校验当前登录人是否拥有该房产。</p>
     *
     * @param ownerId 业主用户 ID
     * @return 房产列表；无房产时返回空列表
     */
    List<PropertyResponseVO> listByOwner(Long ownerId);

    /**
     * 管理端代业主新增房产。
     *
     * <p>归属业主由入参指定，会校验该账号存在且为 OWNER 角色；第一套自动设为默认。</p>
     *
     * @param requestVO 房产信息与归属业主 ID
     * @return 新房产 ID
     * @throws com.ycc.zhixiucloud.common.exception.BizException 业主不存在（404）、
     *         不是业主（400）或房产重复（409）
     */
    Long createForOwner(AdminPropertySaveRequestVO requestVO);

    /**
     * 管理端修改房产。
     *
     * <p>不校验当前登录人归属；若入参指定了新的 ownerId，会先校验其为业主角色，
     * 从而支持把房产转移到另一位业主名下。</p>
     *
     * @param id        房产 ID
     * @param requestVO 修改内容（可含新的归属业主）
     * @throws com.ycc.zhixiucloud.common.exception.BizException 房产不存在（404）、
     *         新业主不存在/不是业主或房产重复（409）
     */
    void updateByAdmin(Long id, AdminPropertySaveRequestVO requestVO);

    /**
     * 管理端删除房产。
     *
     * <p>不校验归属；删除默认房产后同样会自动补齐一套默认房产。</p>
     *
     * @param id 房产 ID
     * @throws com.ycc.zhixiucloud.common.exception.BizException 房产不存在时抛出 404
     */
    void deleteByAdmin(Long id);
}