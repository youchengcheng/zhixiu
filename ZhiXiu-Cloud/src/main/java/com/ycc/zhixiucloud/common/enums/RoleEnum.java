package com.ycc.zhixiucloud.common.enums;

import lombok.Getter;

/**
 * 角色编码枚举。
 *
 * <p>与 role 表 role_code 一致，也是 Spring Security 角色前缀的来源
 * （ROLE_OWNER / ROLE_WORKER / ROLE_ADMIN），用于接口权限判断与前端菜单控制。</p>
 */
@Getter
public enum RoleEnum {

    /** 业主：提交报修、查看自家工单、确认完工与评价 */
    OWNER("OWNER", "业主"),
    /** 维修师傅：接单/抢单、处理工单、提交完工 */
    WORKER("WORKER", "维修师傅"),
    /** 物业管理员：派单、审核师傅、查看统计与后台管理 */
    ADMIN("ADMIN", "物业管理员");

    /** 落库编码，同时也是 JWT roleCode 声明值 */
    private final String code;

    /** 前端展示文案 */
    private final String name;

    RoleEnum(String code, String name) {
        // 落库编码
        this.code = code;
        // 展示文案
        this.name = name;
    }

    /**
     * 根据角色编码查找角色。
     *
     * @param code 角色编码，允许为 null
     * @return 匹配的枚举；入参为 null 或编码非法时返回 null
     */
    public static RoleEnum of(String code) {
        // 遍历所有枚举项匹配编码
        for (RoleEnum item : values()) {
            // 编码一致即命中
            if (item.code.equals(code)) {
                // 返回匹配到的枚举
                return item;
            }
        }
        // 未匹配到返回 null
        return null;
    }

    /**
     * 根据角色编码取中文角色名。
     *
     * @param code 角色编码
     * @return 中文角色名；编码无法识别时原样返回 code
     */
    public static String nameOf(String code) {
        // 先按编码查找枚举
        RoleEnum item = of(code);
        // 查不到时回退原编码
        return item == null ? code : item.name;
    }

    /**
     * 生成 Spring Security 使用的角色名。
     *
     * <p>hasRole('OWNER') 对应的权限标识为 ROLE_OWNER。</p>
     *
     * @return "ROLE_" + 角色编码
     */
    public String authority() {
        // 拼上 Spring Security 的 ROLE_ 前缀
        return "ROLE_" + code;
    }
}