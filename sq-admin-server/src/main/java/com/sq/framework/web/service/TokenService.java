package com.sq.framework.web.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.sq.common.constant.CacheConstants;
import com.sq.common.constant.Constants;
import com.sq.common.core.domain.model.LoginUser;
import com.sq.common.core.redis.RedisCache;
import com.sq.common.crypto.Sm3Utils;
import com.sq.common.utils.ServletUtils;
import com.sq.common.utils.StringUtils;
import com.sq.common.utils.ip.AddressUtils;
import com.sq.common.utils.ip.IpUtils;
import com.sq.common.utils.uuid.IdUtils;
import eu.bitwalker.useragentutils.UserAgent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * token 验证处理。
 * <p>
 * 自实现三段 JWT（header.payload.signature），签名算法 SM3-HMAC（header.alg=SM3-HMAC）。
 * 不再依赖 jjwt，避免 jjwt 0.9.x 已知漏洞与对 RSA/HSxxx 算法的误用。
 *
 * @author tzt
 */
@Component
public class TokenService {

    public static final String ALG = "SM3-HMAC";

    @Value("${token.header}")
    private String header;

    @Value("${token.secret}")
    private String secret;

    @Value("${token.expireTime}")
    private int expireTime;

    protected static final long MILLIS_SECOND = 1000;
    protected static final long MILLIS_MINUTE = 60 * MILLIS_SECOND;
    private static final Long MILLIS_MINUTE_TEN = 20 * 60 * 1000L;

    @Autowired
    private RedisCache redisCache;

    public LoginUser getLoginUser(HttpServletRequest request) {
        String token = getToken(request);
        if (StringUtils.isNotEmpty(token)) {
            try {
                Map<String, Object> claims = parseToken(token);
                String uuid = (String) claims.get(Constants.LOGIN_USER_KEY);
                String userKey = getTokenKey(uuid);
                return redisCache.getCacheObject(userKey);
            } catch (Exception ignore) {
                // 解析失败返回 null，由调用方决定是否拒绝请求
            }
        }
        return null;
    }

    public void setLoginUser(LoginUser loginUser) {
        if (StringUtils.isNotNull(loginUser) && StringUtils.isNotEmpty(loginUser.getToken())) {
            refreshToken(loginUser);
        }
    }

    public void delLoginUser(String token) {
        if (StringUtils.isNotEmpty(token)) {
            String userKey = getTokenKey(token);
            redisCache.deleteObject(userKey);
        }
    }

    public String createToken(LoginUser loginUser) {
        String token = IdUtils.fastUUID();
        loginUser.setToken(token);
        setUserAgent(loginUser);
        refreshToken(loginUser);

        Map<String, Object> claims = new HashMap<>();
        claims.put(Constants.LOGIN_USER_KEY, token);
        return createToken(claims);
    }

    public void verifyToken(LoginUser loginUser) {
        long expireTime = loginUser.getExpireTime();
        long currentTime = System.currentTimeMillis();
        if (expireTime - currentTime <= MILLIS_MINUTE_TEN) {
            refreshToken(loginUser);
        }
    }

    public void refreshToken(LoginUser loginUser) {
        loginUser.setLoginTime(System.currentTimeMillis());
        loginUser.setExpireTime(loginUser.getLoginTime() + expireTime * MILLIS_MINUTE);
        String userKey = getTokenKey(loginUser.getToken());
        redisCache.setCacheObject(userKey, loginUser, expireTime, TimeUnit.MINUTES);
    }

    public void setUserAgent(LoginUser loginUser) {
        UserAgent userAgent = UserAgent.parseUserAgentString(ServletUtils.getRequest().getHeader("User-Agent"));
        String ip = IpUtils.getIpAddr(ServletUtils.getRequest());
        loginUser.setIpaddr(ip);
        loginUser.setLoginLocation(AddressUtils.getRealAddressByIP(ip));
        loginUser.setBrowser(userAgent.getBrowser().getName());
        loginUser.setOs(userAgent.getOperatingSystem().getName());
    }

    /**
     * 自实现 JWT：header.payload.signature，签名算法 SM3-HMAC。
     */
    private String createToken(Map<String, Object> claims) {
        Map<String, Object> header = new HashMap<>();
        header.put("alg", ALG);
        header.put("typ", "JWT");
        String headerB64 = base64UrlEncode(JSON.toJSONString(header).getBytes(StandardCharsets.UTF_8));
        String payloadB64 = base64UrlEncode(JSON.toJSONString(claims).getBytes(StandardCharsets.UTF_8));
        String signingInput = headerB64 + "." + payloadB64;
        byte[] mac = Sm3Utils.hmac(secret.getBytes(StandardCharsets.UTF_8),
                signingInput.getBytes(StandardCharsets.UTF_8));
        String signature = base64UrlEncode(mac);
        return signingInput + "." + signature;
    }

    /**
     * 解析 SM3-HMAC JWT。校验 alg、签名匹配；任一失败抛异常。
     */
    private Map<String, Object> parseToken(String token) {
        if (token == null) {
            throw new IllegalArgumentException("token 为空");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("token 格式错误");
        }
        byte[] headerBytes = base64UrlDecode(parts[0]);
        JSONObject header = JSON.parseObject(new String(headerBytes, StandardCharsets.UTF_8));
        if (!ALG.equals(header.getString("alg"))) {
            throw new IllegalArgumentException("token alg 不被支持: " + header.getString("alg"));
        }

        String signingInput = parts[0] + "." + parts[1];
        byte[] expected = Sm3Utils.hmac(secret.getBytes(StandardCharsets.UTF_8),
                signingInput.getBytes(StandardCharsets.UTF_8));
        byte[] actual = base64UrlDecode(parts[2]);
        if (!constantTimeEquals(expected, actual)) {
            throw new IllegalArgumentException("token 签名校验失败");
        }

        byte[] payloadBytes = base64UrlDecode(parts[1]);
        return JSON.parseObject(new String(payloadBytes, StandardCharsets.UTF_8));
    }

    public String getUsernameFromToken(String token) {
        Map<String, Object> claims = parseToken(token);
        Object sub = claims.get("sub");
        return sub == null ? null : sub.toString();
    }

    private String getToken(HttpServletRequest request) {
        String token = request.getHeader(header);
        if (StringUtils.isNotEmpty(token) && token.startsWith(Constants.TOKEN_PREFIX)) {
            token = token.replace(Constants.TOKEN_PREFIX, "");
        }
        return token;
    }

    private String getTokenKey(String uuid) {
        return CacheConstants.LOGIN_TOKEN_KEY + uuid;
    }

    private static String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static byte[] base64UrlDecode(String s) {
        return Base64.getUrlDecoder().decode(s);
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }
}
