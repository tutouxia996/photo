package com.sq.bus.service.ai;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.sq.bus.config.AlbumProperties;
import com.sq.common.exception.ServiceException;
import com.sq.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OpenAI 兼容多模态接口：从照片识别地标/景区名称。
 */
@Service
public class VisionLandmarkClient {

    private static final Logger log = LoggerFactory.getLogger(VisionLandmarkClient.class);

    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*\\}");

    @Autowired
    private AlbumProperties albumProperties;

    public boolean isConfigured() {
        AlbumProperties.AiLandmarkConfig cfg = cfg();
        return cfg != null && cfg.isEnabled() && StringUtils.isNotEmpty(trim(cfg.getApiKey()));
    }

    /**
     * @param imageBase64 JPEG/PNG base64（不含 data: 前缀）
     * @param mimeType    image/jpeg 等
     * @param regionHint  已知粗定位区域，如「北京市东城区 地坛公园」
     * @param poiNames    该区域内可选 POI 白名单（动态拉取，不写死景区）
     */
    public Map<String, Object> recognize(String imageBase64, String mimeType, String regionHint,
                                         java.util.List<String> poiNames) {
        if (!isConfigured()) {
            throw new ServiceException("未配置 AI 地标识别：请在 application-local.yml 设置 album.aiLandmark.apiKey（或环境变量 AI_LANDMARK_API_KEY）");
        }
        if (StringUtils.isEmpty(imageBase64)) {
            throw new ServiceException("图片数据为空");
        }
        AlbumProperties.AiLandmarkConfig cfg = cfg();
        String mime = StringUtils.isEmpty(mimeType) ? "image/jpeg" : mimeType.trim();
        String dataUrl = "data:" + mime + ";base64," + imageBase64;
        String prompt = buildPrompt(regionHint, poiNames);

        JSONObject body = new JSONObject();
        body.put("model", StringUtils.isEmpty(cfg.getModel()) ? "qwen-vl-plus" : cfg.getModel().trim());
        JSONArray messages = new JSONArray();
        JSONObject user = new JSONObject();
        user.put("role", "user");
        JSONArray content = new JSONArray();
        JSONObject textPart = new JSONObject();
        textPart.put("type", "text");
        textPart.put("text", prompt);
        content.add(textPart);
        JSONObject imagePart = new JSONObject();
        imagePart.put("type", "image_url");
        JSONObject imageUrl = new JSONObject();
        imageUrl.put("url", dataUrl);
        imagePart.put("image_url", imageUrl);
        content.add(imagePart);
        user.put("content", content);
        messages.add(user);
        body.put("messages", messages);
        body.put("temperature", 0.1);

        String base = trim(cfg.getBaseUrl());
        if (StringUtils.isEmpty(base)) {
            base = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String url = base + "/chat/completions";

        try {
            JSONObject resp = postJson(url, trim(cfg.getApiKey()), body.toJSONString(),
                    cfg.getConnectTimeoutMs(), cfg.getReadTimeoutMs());
            String text = extractAssistantText(resp);
            return parseLandmarkJson(text);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI landmark recognize failed: {}", e.getMessage());
            throw new ServiceException("AI 地标识别失败：" + e.getMessage());
        }
    }

    /** 兼容旧调用 */
    public Map<String, Object> recognize(String imageBase64, String mimeType, String regionHint) {
        return recognize(imageBase64, mimeType, regionHint, null);
    }

    private String buildPrompt(String regionHint, java.util.List<String> poiNames) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是旅行照片地标识别助手。根据图片中的建筑、景区、招牌文字判断拍摄地点。");
        if (StringUtils.isNotEmpty(regionHint)) {
            sb.append("【强制约束】本相册已知粗定位区域是「").append(regionHint.trim()).append("」。");
            sb.append("只能在该区域内判断，禁止输出区域外的同城其它景点。");
        }
        if (poiNames != null && !poiNames.isEmpty()) {
            sb.append("【候选地点白名单】请尽量从下列名称中选择最匹配的一项作为 landmark（可微调用词但必须对应其中一项）：");
            int n = Math.min(poiNames.size(), 35);
            for (int i = 0; i < n; i++) {
                if (i > 0) {
                    sb.append("、");
                }
                sb.append(poiNames.get(i));
            }
            sb.append("。若都不像，landmark 填区域主景点名。");
        } else {
            sb.append("landmark 填该区域内具体建筑/景点名；不确定则填区域主景点名。");
        }
        sb.append("只输出一个 JSON 对象，不要 markdown，字段：");
        sb.append("landmark(具体景点/建筑名，不确定则为 null)、");
        sb.append("place(市+区或景区名)、");
        sb.append("confidence(0~1)、");
        sb.append("visible_text(图中可读文字，可空)。");
        sb.append("示例：{\"landmark\":\"方泽坛\",\"place\":\"地坛公园\",\"confidence\":0.72,\"visible_text\":\"方泽坛\"}");
        return sb.toString();
    }

    private String extractAssistantText(JSONObject resp) {
        if (resp == null) {
            throw new ServiceException("AI 返回为空");
        }
        if (resp.containsKey("error")) {
            JSONObject err = resp.getJSONObject("error");
            String msg = err == null ? resp.toJSONString() : err.getString("message");
            throw new ServiceException(StringUtils.isEmpty(msg) ? "AI 接口报错" : msg);
        }
        JSONArray choices = resp.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new ServiceException("AI 未返回识别结果");
        }
        JSONObject msg = choices.getJSONObject(0).getJSONObject("message");
        if (msg == null) {
            throw new ServiceException("AI 返回格式异常");
        }
        Object content = msg.get("content");
        if (content == null) {
            return "";
        }
        if (content instanceof JSONArray) {
            JSONArray arr = (JSONArray) content;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.size(); i++) {
                Object item = arr.get(i);
                if (item instanceof JSONObject) {
                    String t = ((JSONObject) item).getString("text");
                    if (StringUtils.isNotEmpty(t)) {
                        sb.append(t);
                    }
                } else if (item != null) {
                    sb.append(item);
                }
            }
            return sb.toString().trim();
        }
        return String.valueOf(content).trim();
    }

    private Map<String, Object> parseLandmarkJson(String text) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("landmark", null);
        row.put("place", null);
        row.put("confidence", 0.0);
        row.put("visibleText", null);
        row.put("raw", text);
        if (StringUtils.isEmpty(text)) {
            return row;
        }
        String jsonText = text;
        Matcher m = JSON_BLOCK.matcher(text);
        if (m.find()) {
            jsonText = m.group();
        }
        try {
            JSONObject obj = JSON.parseObject(jsonText);
            if (obj == null) {
                return row;
            }
            String landmark = asPlain(obj.get("landmark"));
            String place = asPlain(obj.get("place"));
            Double conf = obj.getDouble("confidence");
            String visible = asPlain(obj.get("visible_text"));
            if (visible == null) {
                visible = asPlain(obj.get("visibleText"));
            }
            row.put("landmark", landmark);
            row.put("place", place);
            row.put("confidence", conf == null ? 0.0 : Math.max(0.0, Math.min(1.0, conf)));
            row.put("visibleText", visible);
            return row;
        } catch (Exception e) {
            log.debug("parse landmark json failed: {}", e.getMessage());
            return row;
        }
    }

    private String asPlain(Object obj) {
        if (obj == null) {
            return null;
        }
        String s = String.valueOf(obj).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s) || "[]".equals(s)) {
            return null;
        }
        return s;
    }

    private JSONObject postJson(String url, String apiKey, String jsonBody,
                                int connectTimeoutMs, int readTimeoutMs) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(Math.max(3000, connectTimeoutMs));
        conn.setReadTimeout(Math.max(10000, readTimeoutMs));
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        // OpenRouter 等网关建议带上；其它兼容接口会忽略
        conn.setRequestProperty("HTTP-Referer", "http://localhost");
        conn.setRequestProperty("X-Title", "AlbumAiLandmark");
        byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bytes.length);
        OutputStream os = conn.getOutputStream();
        os.write(bytes);
        os.flush();
        os.close();

        int code = conn.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                code >= 400 ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        conn.disconnect();
        JSONObject resp = JSON.parseObject(sb.toString());
        if (code >= 400) {
            String msg = resp == null ? sb.toString() : String.valueOf(resp.get("error"));
            throw new ServiceException("AI 接口 HTTP " + code + "：" + msg);
        }
        return resp;
    }

    private AlbumProperties.AiLandmarkConfig cfg() {
        return albumProperties == null ? null : albumProperties.getAiLandmark();
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
