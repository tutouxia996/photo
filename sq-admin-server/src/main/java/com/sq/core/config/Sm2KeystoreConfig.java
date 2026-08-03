package com.sq.core.config;

import com.sq.common.crypto.Sm2KeystoreLoader;
import com.sq.common.crypto.Sm2KeystoreLoader.Sm2KeyPair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyStore;

/**
 * SM2 keystore 配置：启动期加载 PKCS12 keystore，解出公私钥对作为 Bean。
 * <p>
 * 加载顺序：sq.crypto.sm2.external（绝对路径）&gt; sq.crypto.sm2.classpath。
 * 外部路径已配置但加载失败将抛异常拒绝启动，杜绝静默 fallback 至 classpath 默认密钥。
 *
 * @author tzt
 */
@Configuration
public class Sm2KeystoreConfig {

    @Value("${sq.crypto.sm2.external:}")
    private String externalPath;

    @Value("${sq.crypto.sm2.classpath:keystore/sq-sm2.p12}")
    private String classpathPath;

    @Value("${sq.crypto.sm2.store-password}")
    private String storePassword;

    @Value("${sq.crypto.sm2.key-password}")
    private String keyPassword;

    @Value("${sq.crypto.sm2.alias:sq-sm2}")
    private String alias;

    @Bean
    public Sm2KeyPair sm2KeyPair() {
        KeyStore ks = Sm2KeystoreLoader.load(externalPath, classpathPath, storePassword.toCharArray());
        return Sm2KeystoreLoader.extract(ks, alias, keyPassword.toCharArray());
    }
}
