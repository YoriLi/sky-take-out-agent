package com.sky.controller.admin;

import com.sky.constant.MessageConstant;
import com.sky.result.Result;
import com.sky.utils.AliOssUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 通用接口
 */
@RestController
@RequestMapping("/admin/common")
@Tag(name = "通用接口")
@Slf4j
public class CommonController {

    private static final Set<String> DEFAULT_ALLOWED = new HashSet<>(Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));

    @Autowired
    private AliOssUtil aliOssUtil;

    @Value("${sky.upload.max-size-bytes:5242880}")
    private long maxSizeBytes;

    @Value("${sky.upload.allowed-extensions:jpg,jpeg,png,gif,webp}")
    private String allowedExtensions;

    @PostMapping("/upload")
    @Operation(summary = "文件上传",
            requestBody = @RequestBody(required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schemaProperties = @SchemaProperty(name = "file",
                                    schema = @Schema(type = "string", format = "binary", description = "待上传的文件")))))
    @Parameter(name = "file", hidden = true)
    public Result<String> upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.error(MessageConstant.UPLOAD_FAILED);
        }
        if (file.getSize() > maxSizeBytes) {
            return Result.error(MessageConstant.FILE_SIZE_ERROR);
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            return Result.error(MessageConstant.FILE_TYPE_ERROR);
        }

        String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase(Locale.ROOT);
        Set<String> allowed = Arrays.stream(allowedExtensions.split(","))
                .map(String::trim)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        if (allowed.isEmpty()) {
            allowed = DEFAULT_ALLOWED;
        }
        if (!allowed.contains(extension)) {
            return Result.error(MessageConstant.FILE_TYPE_ERROR);
        }

        try {
            String objectName = UUID.randomUUID() + "." + extension;
            String filePath = aliOssUtil.upload(file.getBytes(), objectName);
            return Result.success(filePath);
        } catch (IOException e) {
            log.error("文件上传失败", e);
        }

        return Result.error(MessageConstant.UPLOAD_FAILED);
    }

}
