package com.ycc.zhixiucloud.common.aspect;

import cn.hutool.json.JSONUtil;
import com.ycc.zhixiucloud.common.annotation.ApiLog;
import com.ycc.zhixiucloud.common.enums.OperatorTypeEnum;
import com.ycc.zhixiucloud.common.util.IpUtil;
import com.ycc.zhixiucloud.dto.OperationLogMessage;
import com.ycc.zhixiucloud.security.LoginUser;
import com.ycc.zhixiucloud.security.UserIdHolder;
import com.ycc.zhixiucloud.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 接口日志切面。
 *
 * <p>对应 common.aspect 模块，只织入标注了 {@link ApiLog} 的 Controller 方法。
 * 职责分两部分：一是向文件日志输出“请求开始 / 请求结束 / 请求异常”，便于排查问题；
 * 二是把访问日志（操作人、模块、URI、脱敏入参、IP、耗时等）交给
 * {@link OperationLogService} 异步落库 operation_log。</p>
 *
 * <p>设计要点：日志采集失败不能影响业务主流程；密码、身份证、手机号等敏感字段
 * 在入库前必须先脱敏；SSE 流式响应不能序列化，只记录占位符。
 * 登录、注册等认证接口不要标注，避免密码进入日志。</p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ApiLogAspect {

    /** 结果：成功 */
    private static final int RESULT_SUCCESS = 1;

    /** 结果：失败 */
    private static final int RESULT_FAIL = 0;

    /** 入库请求参数的最大长度，超长截断，避免撑爆日志表 */
    private static final int MAX_PARAMS_LENGTH = 2000;

    /** 入库错误信息的最大长度 */
    private static final int MAX_ERROR_LENGTH = 500;

    /** 操作日志服务：负责把日志消息异步投递到 MQ，避免阻塞业务线程 */
    private final OperationLogService operationLogService;

    /**
     * 环绕通知：统计 Controller 方法耗时并输出请求日志，同时采集访问日志。
     *
     * <p>无论目标方法成功还是抛异常都会记录日志；采集日志本身不改变业务返回值，
     * 异常也会在记录后原样抛出，不做吞异常处理。</p>
     *
     * @param pjp    目标方法连接点，用于获取方法签名、入参并执行目标方法
     * @param apiLog 目标方法上的 {@link ApiLog} 注解实例
     * @return 目标方法的原始返回值
     * @throws Throwable 目标方法抛出的异常，记录后继续向上抛出
     */
    @Around("@annotation(apiLog)")
    public Object around(ProceedingJoinPoint pjp, ApiLog apiLog) throws Throwable {
        // 取当前线程绑定的请求上下文
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        // 非 Web 场景时请求为空，容错处理
        HttpServletRequest request = attrs == null ? null : attrs.getRequest();
        // 请求 URI，取不到时用 "-" 占位
        String requestUri = request == null ? "-" : request.getRequestURI();
        // 目标类全限定名
        String requestClass = pjp.getTarget().getClass().getName();
        // 目标方法名
        String requestMethod = pjp.getSignature().getName();

        // 打印请求开始日志，含入参
        log.info("====== 请求开始: [{}], 入参: {}, 请求类: {}, 请求方法: {} =================================== ",
                requestUri, toJson(pjp.getArgs()), requestClass, requestMethod);

        // 记录起始时间，用于统计耗时
        long start = System.currentTimeMillis();
        // 在 try 中执行目标方法，异常统一在此处记录
        try {
            // 执行目标 Controller 方法
            Object result = pjp.proceed();
            // 计算本次调用耗时
            long cost = System.currentTimeMillis() - start;
            // SSE 流式接口不序列化返回值，用占位串
            String resultJson = result instanceof SseEmitter ? "[SSE]" : toJson(result);
            // 打印请求结束日志，含耗时与出参
            log.info("====== 请求结束: [{}], 耗时: {}ms, 出参: {} =================================== ",
                    requestUri, cost, resultJson);
            // 采集访问日志（成功，无错误信息）
            recordLog(request, apiLog, pjp.getArgs(), cost, RESULT_SUCCESS, null);
            // 原样返回业务结果
            return result;
        // 目标方法抛异常时记录耗时与错误后原样抛出
        } catch (Throwable e) {
            // 计算异常时的耗时
            long cost = System.currentTimeMillis() - start;
            // 打印请求异常日志
            log.error("====== 请求异常: [{}], 耗时: {}ms, 异常: {} =================================== ",
                    requestUri, cost, e.getMessage(), e);
            // 采集访问日志（失败，带异常信息）
            recordLog(request, apiLog, pjp.getArgs(), cost, RESULT_FAIL, e.getMessage());
            // 异常继续抛出，不吞掉
            throw e;
        }
    }

    /**
     * 采集访问日志并交给服务层异步落库。
     *
     * <p>日志采集属于旁路功能，失败只记日志，不能影响业务请求；因此本方法内部
     * 只做数据组装，由 {@link OperationLogService#record} 负责异步投递。</p>
     *
     * @param request  当前 HTTP 请求，非 Web 场景可能为 null
     * @param apiLog   目标方法上的日志注解
     * @param args     目标方法入参，用于脱敏后记录
     * @param cost     本次调用耗时（毫秒）
     * @param result   执行结果：0 失败、1 成功
     * @param errorMsg 异常信息，成功时为 null
     */
    private void recordLog(HttpServletRequest request, ApiLog apiLog, Object[] args,
                           long cost, int result, String errorMsg) {
        // 取当前登录用户，未登录为 null
        LoginUser loginUser = UserIdHolder.get();
        // 组装日志消息，各字段做空值兜底
        OperationLogMessage message = new OperationLogMessage(
                // 操作人 ID
                loginUser == null ? null : loginUser.getUserId(),
                // 操作人登录名
                loginUser == null ? null : loginUser.getUsername(),
                // 未登录时记为系统操作
                loginUser == null ? OperatorTypeEnum.SYSTEM.getCode() : loginUser.getRoleCode(),
                // 模块（来自注解）
                apiLog.module(),
                // 动作（来自注解）
                apiLog.action(),
                // HTTP 方法
                request == null ? null : request.getMethod(),
                // 请求地址
                request == null ? null : request.getRequestURI(),
                // 入参脱敏后入库
                maskParams(args),
                // 客户端真实 IP
                IpUtil.getClientIp(request),
                // 结果码：0失败 1成功
                result,
                // 错误信息按上限截断
                truncate(errorMsg, MAX_ERROR_LENGTH),
                // 耗时（毫秒）
                (int) cost,
                // 采集时间戳
                System.currentTimeMillis());
        // 交给服务层异步投递到 MQ
        operationLogService.record(message);
    }

    /**
     * 入库存参脱敏并截断。
     *
     * <p>先序列化为 JSON，再用正则替换敏感字段：密码类整体打码；身份证号保留
     * 前 4 位和后 4 位；手机号类字段保留前 3 位和后 4 位。最后按
     * {@link #MAX_PARAMS_LENGTH} 截断，防止超长请求撑大日志表。</p>
     *
     * @param args 目标方法入参数组
     * @return 脱敏且截断后的 JSON 字符串
     */
    private String maskParams(Object[] args) {
        // 入参先整体序列化为 JSON
        String json = toJson(args);
        // 密码类字段整体打码为 ***
        json = json.replaceAll("(\"(?:password|oldPassword|newPassword|confirmPassword)\"\\s*:\\s*)\"[^\"]*\"",
                "$1\"***\"");
        // 身份证号保留前 4 后 4
        json = json.replaceAll("(\"idCard\"\\s*:\\s*)\"(\\w{4})\\w+(\\w{4})\"", "$1\"$2**********$3\"");
        // 手机号保留前 3 后 4
        json = json.replaceAll("(\"(?:phone|contactPhone|contact)\"\\s*:\\s*)\"(\\d{3})\\d+(\\d{4})\"",
                "$1\"$2****$3\"");
        // 超长截断后返回
        return truncate(json, MAX_PARAMS_LENGTH);
    }

    /**
     * 按最大长度截断文本。
     *
     * @param text      原文本，null 或空白串直接返回
     * @param maxLength 允许的最大字符数
     * @return 原文本或截断后的文本
     */
    private String truncate(String text, int maxLength) {
        // 空串或 null 原样返回
        if (!StringUtils.hasText(text)) {
            // 直接返回原文本
            return text;
        }
        // 未超长原样返回，超长则截断
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    /**
     * 参数或结果转 JSON。
     *
     * <p>序列化失败不抛异常，避免日志逻辑影响主流程。</p>
     *
     * @param obj 待序列化对象
     * @return JSON 字符串；失败时返回固定占位串
     */
    private String toJson(Object obj) {
        // 尝试序列化，失败不影响主流程
        try {
            // 序列化为 JSON 字符串
            return JSONUtil.toJsonStr(obj);
        // 捕获序列化异常
        } catch (Exception e) {
            // 序列化失败返回占位串，不中断主流程
            return "[serialize error]";
        }
    }
}
