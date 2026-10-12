package com.sq.system.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.sq.common.config.ProjectConfig;
import com.sq.common.utils.StringUtils;
import com.sq.common.utils.http.HttpUtils;
import com.sq.common.utils.ip.IpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP 归属地解析，返回省/市/区等详细中文地名。
 */
public final class IpLocationUtils {
    private static final Logger log = LoggerFactory.getLogger(IpLocationUtils.class);

    /** 太平洋网络 IP 接口（须用 HTTPS；HTTP 会被 403 拒绝，旧逻辑会落成「XX XX」） */
    private static final String IP_URL = "https://whois.pconline.com.cn/ipJson.jsp";
    private static final String UNKNOWN = "未知";
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private IpLocationUtils() {
    }

    /**
     * 根据 IP 解析详细中文登录地点（省 市 区）。
     */
    public static String getRealAddressByIP(String ip) {
        if (StringUtils.isEmpty(ip)) {
            return UNKNOWN;
        }
        if (IpUtils.internalIp(ip)) {
            return "内网IP";
        }
        String cached = CACHE.get(ip);
        if (cached != null) {
            return cached;
        }
        if (!ProjectConfig.isAddressEnabled()) {
            return UNKNOWN;
        }
        try {
            String rspStr = HttpUtils.sendGet(IP_URL, "ip=" + ip + "&json=true", "GBK");
            if (StringUtils.isEmpty(rspStr)) {
                log.error("获取地理位置异常，接口无响应 {}", ip);
                return UNKNOWN;
            }
            JSONObject obj = JSON.parseObject(rspStr);
            String location = formatChineseAddress(obj);
            if (StringUtils.isEmpty(location) || isPlaceholder(location)) {
                location = UNKNOWN;
            }
            CACHE.put(ip, location);
            return location;
        } catch (Exception e) {
            log.error("获取地理位置异常 {}", ip, e);
            return UNKNOWN;
        }
    }

    /**
     * 优先拼接 省/市/区；否则使用接口返回的完整 addr（去掉尾部运营商）。
     */
    private static String formatChineseAddress(JSONObject obj) {
        if (obj == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        addPart(parts, obj.getString("pro"));
        addPart(parts, obj.getString("city"));
        addPart(parts, obj.getString("region"));
        if (!parts.isEmpty()) {
            // 去重保序，避免直辖市「北京市 北京市」
            Set<String> unique = new LinkedHashSet<>(parts);
            return String.join(" ", unique);
        }
        String addr = obj.getString("addr");
        if (StringUtils.isEmpty(addr)) {
            return null;
        }
        addr = addr.trim();
        if (isPlaceholder(addr)) {
            return null;
        }
        // addr 常见格式：广东省深圳市南山区 电信
        int space = addr.lastIndexOf(' ');
        if (space > 0) {
            String tail = addr.substring(space + 1);
            if (isIspSuffix(tail)) {
                addr = addr.substring(0, space).trim();
            }
        }
        return addr;
    }

    private static void addPart(List<String> parts, String value) {
        if (StringUtils.isNotEmpty(value) && !"XX".equalsIgnoreCase(value)) {
            parts.add(value.trim());
        }
    }

    private static boolean isPlaceholder(String text) {
        if (StringUtils.isEmpty(text)) {
            return true;
        }
        String normalized = text.replace(" ", "").trim();
        return "XXXX".equalsIgnoreCase(normalized) || "XX".equalsIgnoreCase(normalized);
    }

    private static boolean isIspSuffix(String text) {
        return text.contains("电信") || text.contains("联通") || text.contains("移动")
                || text.contains("铁通") || text.contains("广电") || text.contains("教育网")
                || text.contains("鹏博士") || text.contains("长城");
    }
}
