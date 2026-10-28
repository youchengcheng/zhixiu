package com.ycc.zhixiucloud.service;

import com.ycc.zhixiucloud.response.FileUploadResponseVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件服务：图片上传到 MinIO，并按对象路径提供读取。
 *
 * <p>职责边界：只处理文件对象本身，不维护业务表关联；业务方保存返回的 URL 即可。</p>
 *
 * <p>主要使用方：通用文件上传接口与公开图片代理接口。</p>
 *
 * <p>关键实现要点：仅允许 image/* 且不超过 10MB；对象名按
 * bizType/yyyy/MM/UUID.扩展名 生成，bizType 仅接受 order/evaluate/avatar/knowledge，
 * 其它值归入 common；上传/读取失败统一转换为业务异常。</p>
 */
public interface FileService {

    /**
     * 上传图片。
     *
     * <p>校验顺序：文件非空 → 大小不超过 10MB → Content-Type 以 image/ 开头。
     * 对象路径由业务类型、年月目录和随机 UUID 组成，避免重名覆盖。</p>
     *
     * @param file    上传文件，不能为空
     * @param bizType 业务类型目录（order/evaluate/avatar/knowledge），未知值归入 common
     * @return 公开访问 URL 与原始文件名
     * @throws com.ycc.zhixiucloud.common.exception.BizException 文件为空或非图片（400）、
     *         超过 10MB（413）、写入 MinIO 失败（500）
     */
    FileUploadResponseVO upload(MultipartFile file, String bizType);

    /**
     * 读取 MinIO 对象字节，供公开图片代理接口输出。
     *
     * @param objectName 对象路径（上传时生成的对象名，不含 bucket）
     * @return 对象字节内容
     * @throws com.ycc.zhixiucloud.common.exception.BizException 对象名为空、
     *         对象不存在或读取失败时抛出 404
     */
    byte[] download(String objectName);
}
