package com.ycc.zhixiucloud.service.impl;

import com.ycc.zhixiucloud.common.exception.BizException;
import com.ycc.zhixiucloud.common.result.ResultCode;
import com.ycc.zhixiucloud.response.FileUploadResponseVO;
import com.ycc.zhixiucloud.service.FileService;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

/**
 * 文件服务实现：图片上传至 MinIO（按 bizType/年/月分目录），并支持对象公开读取。
 *
 * <p>关键设计：</p>
 * <ul>
 *   <li>上传前校验文件非空、大小不超过 10MB、Content-Type 必须为 image/*；</li>
 *   <li>bizType 采用白名单，未知值归入 common 目录，避免写入任意目录；</li>
 *   <li>对象名使用 UUID，避免重名覆盖，同时规避原始文件名中的特殊字符；</li>
 *   <li>访问地址由 minio.public-url 拼接，要求桶在 MinIO 侧配置为公共读；</li>
 *   <li>下载使用 try-with-resources 关闭响应流，防止连接泄漏。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    /** 上传大小上限：10MB */
    private static final long MAX_SIZE = 10L * 1024 * 1024;

    /** 允许的图片前缀 */
    private static final String IMAGE_PREFIX = "image/";

    /** 合法业务类型目录 */
    private static final Set<String> BIZ_TYPES = Set.of("order", "evaluate", "avatar", "knowledge");

    /** 未知 bizType 的兜底目录 */
    private static final String DEFAULT_BIZ_DIR = "common";

    /** 无法识别扩展名时的默认后缀 */
    private static final String DEFAULT_EXT = "jpg";

    /** 目录日期格式：年/月 */
    private static final DateTimeFormatter MONTH_DIR_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM");

    // 对象存储客户端
    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${minio.public-url}")
    private String publicUrl;

    /**
     * 上传图片到 MinIO。
     *
     * <p>依次校验非空、10MB 大小上限与 image/* 类型，然后生成
     * “业务目录/年/月/UUID.后缀”的对象名并写入对象存储，最后拼接公开访问 URL。
     * 每次调用都会新增一个 MinIO 对象，重复上传不会覆盖旧文件。</p>
     *
     * @param file    上传的图片文件
     * @param bizType 业务类型（order/evaluate/avatar/knowledge），未知值归入 common
     * @return 文件公开访问地址与原始文件名
     * @throws BizException 文件为空、超过大小限制、非图片类型或写入 MinIO 失败时抛出
     */
    @Override
    public FileUploadResponseVO upload(MultipartFile file, String bizType) {
        // 校验文件非空
        if (file == null || file.isEmpty()) {
            // 空文件直接拒绝
            throw BizException.of(ResultCode.PARAM_ERROR, "上传文件不能为空");
        }
        // 校验大小不超过 10MB
        if (file.getSize() > MAX_SIZE) {
            // 超出上限返回文件过大错误
            throw BizException.of(ResultCode.FILE_TOO_LARGE, "文件大小不能超过 10MB");
        }
        // 取文件的 content-type
        String contentType = file.getContentType();
        // 非图片类型一律拒绝
        if (contentType == null || !contentType.startsWith(IMAGE_PREFIX)) {
            // 类型不合法直接拒绝
            throw BizException.of(ResultCode.PARAM_ERROR, "仅支持上传图片文件");
        }
        // 原始文件名
        String originalName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename() : "image";
        // 解析扩展名，缺省用 jpg
        String ext = StringUtils.getFilenameExtension(originalName);
        // 扩展名缺失时回退默认后缀
        String suffix = StringUtils.hasText(ext) ? ext.toLowerCase() : DEFAULT_EXT;
        // 生成对象路径：业务目录/年/月/uuid.后缀
        String objectName = buildObjectName(bizType, suffix);
        // 写入 MinIO 对象
        putObject(file, objectName, contentType);
        // 拼接公开访问地址
        String url = publicUrl + "/" + bucket + "/" + objectName;
        // 记录上传日志
        log.info("上传文件成功：object={}, size={}", objectName, file.getSize());
        // 组装并返回出参
        return FileUploadResponseVO.builder()
                .url(url)
                .name(originalName)
                // 构建上传出参
                .build();
    }

    /**
     * 下载 MinIO 对象为字节数组。
     *
     * <p>使用 try-with-resources 确保 GetObjectResponse 关闭，释放底层 HTTP 连接；
     * 对象不存在、无权限等异常统一转换为“文件不存在”，避免向外暴露存储细节。</p>
     *
     * @param objectName 对象在桶内的完整路径
     * @return 对象的全部字节内容
     * @throws BizException 路径为空或读取失败时抛出
     */
    @Override
    public byte[] download(String objectName) {
        // 对象路径为空直接判为不存在
        if (!StringUtils.hasText(objectName)) {
            // 空路径按不存在处理
            throw BizException.of(ResultCode.NOT_FOUND, "文件不存在");
        }
        // try-with-resources 确保响应流关闭，避免连接泄漏
        try (GetObjectResponse response = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .build())) {
            // 读取对象全部字节
            return response.readAllBytes();
        // 捕获读取异常统一降级为文件不存在
        } catch (Exception ex) {
            // 读取失败按资源不存在处理
            log.warn("读取文件失败：object={}, msg={}", objectName, ex.getMessage());
            // 外部异常统一按文件不存在返回
            throw BizException.of(ResultCode.NOT_FOUND, "文件不存在");
        }
    }

    /**
     * 生成对象路径：合法 bizType 分目录，未知归入 common。
     *
     * <p>格式为 {@code bizType/yyyy/MM/uuid.ext}，按月份分目录便于运维排查和生命周期管理。</p>
     *
     * @param bizType 业务类型
     * @param suffix  文件扩展名（已转小写）
     * @return 桶内对象路径
     */
    private String buildObjectName(String bizType, String suffix) {
        // 取合法业务目录，未知用兜底目录
        String dir = StringUtils.hasText(bizType) && BIZ_TYPES.contains(bizType) ? bizType : DEFAULT_BIZ_DIR;
        // 生成年/月子目录
        String monthDir = MONTH_DIR_FORMATTER.format(LocalDate.now());
        // 生成无连字符的 UUID 文件名
        String fileName = UUID.randomUUID().toString().replace("-", "");
        // 拼出完整对象名
        return dir + "/" + monthDir + "/" + fileName + "." + suffix;
    }

    /**
     * 将上传流写入 MinIO 对象。
     *
     * <p>partSize 传 -1 表示由 MinIO 根据已知对象大小自动决定分片大小；
     * 失败时记录错误日志并转换为统一的系统异常。</p>
     *
     * @param file        上传文件
     * @param objectName  目标对象路径
     * @param contentType 原始 Content-Type
     * @throws BizException 写入失败时抛出系统错误
     */
    private void putObject(MultipartFile file, String objectName, String contentType) {
        // 流式写入对象存储，失败转为系统异常
        try {
            // 分片大小交由 MinIO 按已知大小自动决定
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    // 流式上传，已知大小，分片大小交给 MinIO 自动决定
                    .stream(file.getInputStream(), file.getSize(), -1)
                    // 保留原始 content-type
                    .contentType(contentType)
                    .build());
        // 捕获写入异常并转为系统错误
        } catch (Exception ex) {
            // 上传失败统一按系统错误抛出
            log.error("上传文件失败：object={}", objectName, ex);
            // 上传异常统一转为系统错误抛出
            throw BizException.of(ResultCode.SYSTEM_ERROR, "文件上传失败");
        }
    }
}
