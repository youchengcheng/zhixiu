package com.ycc.zhixiucloud.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库文件导入表单，复用新增和修改知识接口。
 * 新增必须上传文件，修改时未上传文件则保留已有正文。
 */
@Data
@Schema(description = "知识库文件导入或修改表单")
public class KnowledgeFileRequestVO {

    @NotBlank(message = "请填写知识标题")
    @Size(max = 200, message = "标题不超过 200 个字")
    @Schema(description = "标题")
    private String title;

    @Schema(description = "关联报修类型 ID")
    private Long categoryId;

    @Schema(description = "PDF、DOCX、TXT、MD/Markdown 文件，不超过 10MB；修改时可不上传",
            type = "string", format = "binary")
    private MultipartFile file;

    @Size(max = 255, message = "标签不超过 255 个字")
    @Schema(description = "标签，逗号分隔")
    private String tags;
}
