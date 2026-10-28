package com.ycc.zhixiucloud.controller;

import com.ycc.zhixiucloud.common.annotation.ApiLog;
import com.ycc.zhixiucloud.common.result.Result;
import com.ycc.zhixiucloud.response.FileUploadResponseVO;
import com.ycc.zhixiucloud.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件接口：登录用户上传图片，公开代理访问图片。
 * /upload 需登录；/public/** 免登录，仅用于前端回显已上传的图片。
 */
@Tag(name = "文件")
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
@Validated
public class FileController {

    // 文件业务服务
    private final FileService fileService;

    @Operation(summary = "上传文件")
    @ApiLog(module = "文件", action = "上传文件")
    @PostMapping("/upload")
    public Result<FileUploadResponseVO> upload(@RequestParam("file") MultipartFile file,
                                               @RequestParam(value = "bizType", required = false) String bizType) {
        // 服务层校验图片类型与大小，按 bizType 分目录写入 MinIO 并返回访问地址
        return Result.ok(fileService.upload(file, bizType), "上传成功");
    }

    @Operation(summary = "公开文件访问")
    @GetMapping("/public/**")
    public ResponseEntity<byte[]> publicFile(HttpServletRequest request) {
        // 免登录接口：从请求 URI 截取 /public/ 之后的对象路径，再由服务层读取 MinIO
        String objectName = resolveObjectName(request.getRequestURI());
        // 读取对象字节
        byte[] bytes = fileService.download(objectName);
        // 按扩展名推断图片类型，未知回退二进制流
        MediaType mediaType = MediaTypeFactory.getMediaType(objectName)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        // 返回带正确 Content-Type 的字节流
        return ResponseEntity.ok().contentType(mediaType).body(bytes);
    }

    /** 截取 URI 中 /public/ 之后的对象路径 */
    private String resolveObjectName(String uri) {
        // 定位公开访问前缀
        String marker = "/public/";
        // 找到前缀起始下标
        int index = uri.indexOf(marker);
        // 前缀之后即为对象路径
        return index >= 0 ? uri.substring(index + marker.length()) : "";
    }
}
