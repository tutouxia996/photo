package com.sq.admin.album.web;

import com.sq.common.core.domain.AjaxResult;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 相册上传体积超限时返回可读提示（避免落入通用「未知异常」）。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class AlbumUploadExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public AjaxResult handleMaxUpload(MaxUploadSizeExceededException e) {
        return AjaxResult.error("文件过大，已超过服务端上传上限（单文件约 30GB）。"
                + "本地超大视频建议放到扫描目录用「扫描入库」，避免浏览器整包上传。");
    }
}
