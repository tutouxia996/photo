package com.sq.admin.common.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 微信网站管理员认证（根路径 txt）。
 * 静态文件 + Security 放行双保险；部署机需重启后公网可访问纯文本。
 */
@RestController
public class WechatDomainVerifyController {

    private static final String CONTENT = "f1f90125d9013573d39d5e112236715b48b15578";

    @GetMapping(value = "/edb4b7dbb0adbd9a2474a18d370f479f.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String verify() {
        return CONTENT;
    }
}
