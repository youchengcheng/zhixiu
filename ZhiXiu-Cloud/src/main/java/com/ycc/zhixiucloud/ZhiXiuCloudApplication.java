package com.ycc.zhixiucloud;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 智修云 · 物业报修系统后端启动类。
 *
 * <p>系统采用前后端分离架构，后端以 REST API 服务业主、维修师傅、物业管理员三类角色；
 * 认证使用 JWT（由 JwtAuthenticationFilter 解析），服务端不保存 Session。</p>
 *
 * <p>排除 Spring Security 的 UserDetailsServiceAutoConfiguration，避免启动时自动创建默认用户
 * 并在控制台打印随机密码 —— 本项目的账号体系完全由数据库 + JWT 自行管理。</p>
 *
 * <p>@EnableAsync 开启 Spring 异步能力，异步任务默认使用 AsyncConfig 中名为 taskExecutor 的线程池。</p>
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@MapperScan("com.ycc.zhixiucloud.mapper") // 扫描 MyBatis-Plus Mapper 接口，省去逐个接口标注 @Mapper
@EnableAsync // 启用 @Async，配合 AsyncConfig 的 taskExecutor 执行后台任务
public class ZhiXiuCloudApplication {

    /** 启动入口：初始化 Spring 上下文并加载 application.yml 中的各项配置。 */
    public static void main(String[] args) {
        // 启动 Spring Boot 应用
        SpringApplication.run(ZhiXiuCloudApplication.class, args);
    }

}
