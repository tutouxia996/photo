package com.sq.admin.system.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 首页：合并部署时转发到前端 index.html
 *
 * @author tzt
 */
@Controller
public class SysIndexController {

    /**
     * 访问根路径时返回前端页面（勿再返回纯文本提示）
     */
    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }
}
