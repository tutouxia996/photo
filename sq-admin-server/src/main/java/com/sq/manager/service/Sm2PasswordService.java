package com.sq.manager.service;

import com.sq.common.crypto.Sm2KeystoreLoader.Sm2KeyPair;
import com.sq.common.crypto.Sm2Utils;
import com.sq.common.exception.user.UserPasswordNotMatchException;
import com.sq.common.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;

@Slf4j
@Component
public class Sm2PasswordService {
    private final PrivateKey privateKey;

    @Autowired
    public Sm2PasswordService(Sm2KeyPair sm2KeyPair) {
        this(sm2KeyPair.getPrivateKey());
    }

    public Sm2PasswordService(PrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    public String decryptPassword(String ciphertextHex) {
        if (StringUtils.isEmpty(ciphertextHex)) {
            throw new UserPasswordNotMatchException();
        }
        String preview = ciphertextHex.length() > 100 ? ciphertextHex.substring(0, 100) + "..." : ciphertextHex;
        log.info("收到的密码密文(前100字符): '{}', 长度={}, 纯hex={}", preview, ciphertextHex.length(), ciphertextHex.matches("^[0-9a-fA-F]+$"));
        if (!ciphertextHex.matches("^[0-9a-fA-F]+$")) {
            log.warn("前端密码密文不是纯 hex，非 hex 前 50 字符='{}'", ciphertextHex.length() > 50 ? ciphertextHex.substring(0, 50) : ciphertextHex);
            throw new UserPasswordNotMatchException();
        }
        try {
            byte[] plain = Sm2Utils.decryptHex(privateKey, ciphertextHex);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("SM2 解密密码失败", e);
            throw new UserPasswordNotMatchException();
        }
    }
}
