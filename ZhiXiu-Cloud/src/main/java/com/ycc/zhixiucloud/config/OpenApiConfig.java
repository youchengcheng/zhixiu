package com.ycc.zhixiucloud.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger 接口文档配置。
 *
 * <p>接口扫描范围由 application.yml 的 springdoc.packages-to-scan 指定（controller 包），
 * 本类只补充文档元数据与全局 JWT Bearer 鉴权方案：在 Swagger UI / Knife4j 页面右上角
 * 「Authorize」填入登录返回的 Token 后，后续调试请求会自动携带 Authorization 头，
 * 与 JwtAuthenticationFilter 的解析规则保持一致。</p>
 */
@Configuration
public class OpenApiConfig {

    /** 全局鉴权方案名，SecurityRequirement 通过该名称引用下方的 SecurityScheme */
    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    /**
     * 构建 OpenAPI 文档描述。
     * 全局 SecurityRequirement 使所有接口默认采用 bearerAuth，无需逐个方法标注 @SecurityRequirement。
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                // 文档标题、版本与说明，展示在文档页顶部
                .info(new Info()
                        .title("智修云 · 物业报修系统 API")
                        .version("1.0")
                        .description("业主 / 维修师傅 / 物业管理员三角色接口，统一响应体 { code, message, data }"))
                // 注册 HTTP Bearer 鉴权方案，请求头格式为 "Authorization: Bearer <token>"
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .in(SecurityScheme.In.HEADER)
                                .name("Authorization")))
                // 声明全局应用上述鉴权方案
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}
