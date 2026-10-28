package com.ycc.zhixiucloud.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口访问日志注解。
 *
 * <p>标注在 Controller 方法上，由 {@link com.ycc.zhixiucloud.common.aspect.ApiLogAspect}
 * 通过 AOP 环绕通知织入：一方面输出“请求开始 / 请求结束”日志（含入参、出参、请求类、
 * 请求方法、耗时），另一方面将访问记录异步写入 operation_log 表。</p>
 *
 * <p>使用约定：登录、注册等涉及密码的接口不要标注，避免敏感信息进入日志；
 * 模块名与操作描述通过下面的两个属性传给切面。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiLog {

    /**
     * 所属业务模块。
     *
     * @return 模块名，如「工单」「AI助手」，默认为空串
     */
    String module() default "";

    /**
     * 具体操作描述。
     *
     * @return 操作名，如「提交报修」，默认为空串
     */
    String action() default "";
}
