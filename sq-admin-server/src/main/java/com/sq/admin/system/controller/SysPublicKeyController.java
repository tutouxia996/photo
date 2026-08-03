package com.sq.admin.system.controller;

import com.sq.common.core.domain.AjaxResult;
import com.sq.common.crypto.Sm2KeystoreLoader.Sm2KeyPair;
import com.sq.common.crypto.Sm2Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SM2 公钥发布接口。
 * 前端登录前先调用 GET /system/publicKey 获取 PEM 与 hex 公钥，再用 sm-crypto 加密密码字段。
 * 该接口需配置为 permitAll。
 *
 * @author tzt
 */
@RestController
@RequestMapping("/system")
public class SysPublicKeyController {

    @Autowired
    private Sm2KeyPair sm2KeyPair;

    @GetMapping("/publicKey")
    public AjaxResult publicKey() {
        AjaxResult ok = AjaxResult.success();
        ok.put("pem", Sm2Utils.toPublicKeyPem(sm2KeyPair.getPublicKey()));
        ok.put("hex", Sm2Utils.toUncompressedHex(sm2KeyPair.getPublicKey()));
        ok.put("alg", "SM2");
        ok.put("mode", "C1C3C2");
        return ok;
    }
}
