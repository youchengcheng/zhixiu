package com.ycc.zhixiucloud.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 文件上传出参（POST /api/file/upload），登录用户可用。
 * 当前仅支持图片且单文件不超过 10MB；url 为可直接访问的公开地址。
 */
@Data
@Builder
@Schema(description = "文件上传结果")
public class FileUploadResponseVO {

    @Schema(description = "访问地址")
    private String url;

    /** 上传前的原始文件名；原始名为空时后端回退为 image */
    @Schema(description = "文件名")
    private String name;
}
