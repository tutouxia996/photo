package com.sq.core.crypto;

import javax.crypto.Cipher;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.Permission;
import java.security.PermissionCollection;
import java.util.Map;

/**
 * JDK 8u161 之前默认限制 AES 密钥长度，加载 PKCS12（AES-256）会报 Illegal key size。
 * 在应用最早阶段解除该限制，避免依赖手工替换 JCE policy jar。
 */
public final class JceUnlimitedPolicy {

    private JceUnlimitedPolicy() {
    }

    public static void enable() {
        try {
            if (Cipher.getMaxAllowedKeyLength("AES") >= 256) {
                return;
            }
        } catch (Exception ignored) {
            // continue to force-enable
        }

        try {
            Class<?> jceSecurity = Class.forName("javax.crypto.JceSecurity");
            Class<?> cryptoPermissions = Class.forName("javax.crypto.CryptoPermissions");
            Class<?> cryptoAllPermission = Class.forName("javax.crypto.CryptoAllPermission");

            Field isRestrictedField = jceSecurity.getDeclaredField("isRestricted");
            removeFinal(isRestrictedField);
            isRestrictedField.set(null, Boolean.FALSE);

            Field defaultPolicyField = jceSecurity.getDeclaredField("defaultPolicy");
            defaultPolicyField.setAccessible(true);
            PermissionCollection defaultPolicy = (PermissionCollection) defaultPolicyField.get(null);

            Field permsField = cryptoPermissions.getDeclaredField("perms");
            permsField.setAccessible(true);
            ((Map<?, ?>) permsField.get(defaultPolicy)).clear();

            Field allPermissionField = cryptoAllPermission.getDeclaredField("INSTANCE");
            allPermissionField.setAccessible(true);
            defaultPolicy.add((Permission) allPermissionField.get(null));
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "当前 JDK 不支持 AES-256（常见于 JDK 8u161 之前），且自动解除 JCE 限制失败。"
                            + "请升级到 JDK 8u161+，或安装 Unlimited Strength JCE Policy。原始原因: "
                            + ex.getMessage(),
                    ex);
        }
    }

    private static void removeFinal(Field field) throws Exception {
        field.setAccessible(true);
        try {
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        } catch (NoSuchFieldException ignored) {
            // JDK 12+ 已移除 modifiers 字段，此路径主要服务 JDK 8
        }
    }
}
