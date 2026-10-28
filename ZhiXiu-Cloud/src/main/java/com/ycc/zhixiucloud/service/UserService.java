package com.ycc.zhixiucloud.service;

import com.ycc.zhixiucloud.request.ChangePasswordRequestVO;
import com.ycc.zhixiucloud.request.UpdateProfileRequestVO;
import com.ycc.zhixiucloud.response.UserProfileResponseVO;

/**
 * 用户中心服务：当前登录用户查看/修改个人资料与修改密码。
 *
 * <p>职责边界：只处理“本人资料”，不涉及用户管理（业主管理见 {@link OwnerService}）；
 * 用户名、手机号、角色不允许自助修改。</p>
 *
 * <p>主要使用方：三端通用的“个人中心”页面。</p>
 *
 * <p>关键实现要点：修改资料采用“传值覆盖、空值保留”策略；密码使用 BCrypt 摘要，
 * 修改时校验原密码、两次新密码一致且新密码不能与原密码相同。</p>
 */
public interface UserService {

    /**
     * 查询当前登录用户的个人资料。
     *
     * @return 个人资料（含角色编码）；角色缺失时 roleCode 为 null
     * @throws com.ycc.zhixiucloud.common.exception.BizException 用户不存在（404）
     *         或角色无法识别（403）
     */
    UserProfileResponseVO getProfile();

    /**
     * 修改当前登录用户的个人资料。
     *
     * <p>仅允许修改 realName、email、gender、avatar；未传或空白字段保留原值，
     * 用户名、手机号与角色不可自助修改。</p>
     *
     * @param requestVO 待修改字段
     * @throws com.ycc.zhixiucloud.common.exception.BizException 用户不存在（404）
     *         或角色无法识别（403）
     */
    void updateProfile(UpdateProfileRequestVO requestVO);

    /**
     * 修改当前登录用户的密码。
     *
     * <p>依次校验：原密码正确 → 两次新密码一致 → 新密码不同于原密码；
     * 通过后以 BCrypt 摘要写回数据库。不会主动使已签发的旧令牌失效。</p>
     *
     * @param requestVO 原密码、新密码与确认密码
     * @throws com.ycc.zhixiucloud.common.exception.BizException 原密码错误、
     *         两次新密码不一致或新旧密码相同（400）
     */
    void changePassword(ChangePasswordRequestVO requestVO);
}
