package com.ycc.zhixiucloud.service;

import com.ycc.zhixiucloud.request.ResetPasswordRequestVO;

/**
 * 管理端用户账号服务。
 */
public interface AdminUserService {

    /** 管理员重置指定业主或师傅的登录密码。 */
    void resetPassword(Long userId, ResetPasswordRequestVO requestVO);
}
