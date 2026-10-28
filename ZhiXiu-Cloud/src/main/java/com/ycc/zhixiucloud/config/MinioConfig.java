package com.ycc.zhixiucloud.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 对象存储配置。
 *
 * <p>提供全局唯一的 MinioClient Bean，供 FileServiceImpl 上传与读取图片；
 * 在不存在的首次启动场景下自动创建 Bucket，并幂等设置匿名只读策略，
 * 使前端可通过 minio.public-url 拼接的对象直链访问图片
 * （对应 SecurityConfig 放行的 /api/file/public/** 等公开文件接口）。</p>
 *
 * <p>连接参数全部来自 application.yml 的 minio.*，访问密钥与私钥通过环境变量注入，
 * 不在代码中硬编码。</p>
 */
@Slf4j
@Configuration
public class MinioConfig {

    /** 公开读策略模板：仅允许匿名执行 s3:GetObject，供图片直链/代理访问；%s 由实际 Bucket 名填充 */
    private static final String PUBLIC_READ_POLICY = """
            {
              "Version": "2012-10-17",
              "Statement": [
                {
                  "Effect": "Allow",
                  "Principal": { "AWS": ["*"] },
                  "Action": ["s3:GetObject"],
                  "Resource": ["arn:aws:s3:::%s/*"]
                }
              ]
            }
            """;

    /** MinIO 服务地址（如 http://localhost:9000） */
    @Value("${minio.endpoint}")
    private String endpoint;

    /** 访问密钥（Access Key），由环境变量 MINIO_ACCESS_KEY 注入 */
    @Value("${minio.access-key}")
    private String accessKey;

    /** 私有密钥（Secret Key），由环境变量 MINIO_SECRET_KEY 注入 */
    @Value("${minio.secret-key}")
    private String secretKey;

    /** 图片存储桶名称，默认 repair-images，需与 FileServiceImpl 中上传使用的桶一致 */
    @Value("${minio.bucket}")
    private String bucket;

    /**
     * 构建 MinIO 客户端并完成 Bucket 初始化。
     * 作为单例 Bean 被各服务复用（MinioClient 本身线程安全）。
     *
     * @throws Exception 端点不可达、密钥错误或 Bucket 初始化失败时快速失败，
     *                   避免运行期上传文件时才发现存储不可用
     */
    @Bean
    public MinioClient minioClient() throws Exception {
        // 用端点与密钥构建客户端
        MinioClient client = MinioClient.builder()
                // 指定 MinIO 服务端点
                .endpoint(endpoint)
                // 使用访问密钥与私钥认证
                .credentials(accessKey, secretKey)
                .build();
        // 启动时初始化 Bucket
        initBucket(client);
        // 记录客户端初始化完成
        log.info("初始化 MinIO 客户端，端点：{}，Bucket：{}", endpoint, bucket);
        // 返回初始化完成的客户端 Bean
        return client;
    }

    /**
     * 校验 Bucket 存在，不存在则创建并设置公开读策略。
     * 无论 Bucket 是否新建都会重新设置策略，保证配置被外部改动后重启仍能恢复，即幂等。
     */
    private void initBucket(MinioClient client) throws Exception {
        // 判断目标 Bucket 是否已存在
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        // 不存在才创建
        if (!exists) {
            // 首次启动时创建图片存储桶
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            // 记录创建日志
            log.info("MinIO Bucket 不存在，已创建：{}", bucket);
        }
        // 幂等设置公开读策略：允许匿名读取对象，前端才能直接通过 URL 展示图片
        client.setBucketPolicy(SetBucketPolicyArgs.builder()
                // 指定目标存储桶
                .bucket(bucket)
                // 策略资源用实际 Bucket 名填充（替换模板中的 %s）
                .config(PUBLIC_READ_POLICY.formatted(bucket))
                .build());
        // 记录策略设置完成
        log.info("MinIO Bucket 公开读策略已设置：{}", bucket);
    }
}
