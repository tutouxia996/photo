package com.sq.bus.service.ai;

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
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 通义万相图生图（wan2.5-i2i-preview），异步提交 + 轮询。
 */
@Service
public class WanxiangImg2ImgClient {

    private static final Logger log = LoggerFactory.getLogger(WanxiangImg2ImgClient.class);

    @Autowired
    private AlbumProperties albumProperties;

    public boolean isConfigured() {
        return StringUtils.isNotEmpty(resolveApiKey());
    }

    /**
     * 图生图，返回生成面板 PNG/JPEG 字节。
     */
    public byte[] generatePanel(String imageDataUrl, String prompt, String size) {
        if (!isConfigured()) {
            throw new ServiceException("未配置万相图生图：请在 application-local.yml 设置 album.photoDraw.apiKey（或复用 AI_LANDMARK_API_KEY）");
        }
        if (StringUtils.isEmpty(imageDataUrl)) {
            throw new ServiceException("参考图为空");
        }
        AlbumProperties.PhotoDrawConfig cfg = cfg();
        String model = StringUtils.isEmpty(cfg.getModel()) ? "wan2.5-i2i-preview" : cfg.getModel().trim();
        String outputSize = StringUtils.isEmpty(size) ? "960*1280" : size.trim();

        JSONObject input = new JSONObject();
        input.put("prompt", prompt);
        JSONArray images = new JSONArray();
        images.add(imageDataUrl);
        input.put("images", images);

        JSONObject parameters = new JSONObject();
        parameters.put("prompt_extend", cfg.isPromptExtend());
        parameters.put("n", 1);
        parameters.put("size", outputSize);
        if (StringUtils.isNotEmpty(cfg.getNegativePrompt())) {
            input.put("negative_prompt", cfg.getNegativePrompt().trim());
        }

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("input", input);
        body.put("parameters", parameters);

        String base = apiBase();
        String createUrl = base + "/services/aigc/image2image/image-synthesis";
        try {
            JSONObject createResp = postJson(createUrl, resolveApiKey(), body.toJSONString(),
                    cfg.getConnectTimeoutMs(), cfg.getReadTimeoutMs(), true);
            String taskId = extractTaskId(createResp);
            JSONObject done = pollTask(base, taskId, cfg);
            String imageUrl = extractImageUrl(done);
            return downloadBytes(imageUrl, cfg.getReadTimeoutMs());
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("wanxiang img2img failed: {}", e.getMessage());
            throw new ServiceException("万相图生图失败：" + e.getMessage());
        }
    }

    private JSONObject pollTask(String base, String taskId, AlbumProperties.PhotoDrawConfig cfg) throws Exception {
        String url = base + "/tasks/" + taskId;
        int interval = Math.max(1000, cfg.getPollIntervalMs());
        int maxAttempts = Math.max(10, cfg.getMaxPollAttempts());
        for (int i = 0; i < maxAttempts; i++) {
            JSONObject resp = getJson(url, resolveApiKey(), cfg.getConnectTimeoutMs(), cfg.getReadTimeoutMs());
            JSONObject output = resp.getJSONObject("output");
            if (output == null) {
                throw new ServiceException("万相任务返回异常");
            }
            String status = output.getString("task_status");
            if ("SUCCEEDED".equalsIgnoreCase(status)) {
                return output;
            }
            if ("FAILED".equalsIgnoreCase(status) || "CANCELED".equalsIgnoreCase(status)) {
                String msg = output.getString("message");
                if (StringUtils.isEmpty(msg) && resp.containsKey("message")) {
                    msg = resp.getString("message");
                }
                throw new ServiceException("万相任务失败：" + (StringUtils.isEmpty(msg) ? status : msg));
            }
            Thread.sleep(interval);
        }
        throw new ServiceException("万相图生图超时，请稍后重试");
    }

    private static String extractTaskId(JSONObject resp) {
        if (resp == null) {
            throw new ServiceException("万相返回为空");
        }
        if (resp.containsKey("code") && !"null".equals(String.valueOf(resp.get("code")))) {
            String code = String.valueOf(resp.get("code"));
            if (!code.isEmpty() && !"200".equals(code)) {
                throw new ServiceException("万相提交失败：" + resp.getString("message"));
            }
        }
        JSONObject output = resp.getJSONObject("output");
        if (output == null) {
            throw new ServiceException("万相未返回 task_id");
        }
        String taskId = output.getString("task_id");
        if (StringUtils.isEmpty(taskId)) {
            throw new ServiceException("万相未返回 task_id");
        }
        return taskId.trim();
    }

    private static String extractImageUrl(JSONObject output) {
        JSONArray results = output.getJSONArray("results");
        if (results == null || results.isEmpty()) {
            throw new ServiceException("万相未返回生成图");
        }
        JSONObject first = results.getJSONObject(0);
        if (first == null) {
            throw new ServiceException("万相结果格式异常");
        }
        String url = first.getString("url");
        if (StringUtils.isEmpty(url)) {
            String code = first.getString("code");
            String msg = first.getString("message");
            throw new ServiceException("万相生成失败：" + (StringUtils.isEmpty(msg) ? code : msg));
        }
        return url.trim();
    }

    private byte[] downloadBytes(String imageUrl, int readTimeoutMs) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(imageUrl).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(Math.max(30000, readTimeoutMs));
        conn.setRequestMethod("GET");
        int code = conn.getResponseCode();
        InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (in == null) {
            throw new ServiceException("下载生成图失败 HTTP " + code);
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) {
            bos.write(buf, 0, n);
        }
        in.close();
        conn.disconnect();
        if (code >= 400) {
            throw new ServiceException("下载生成图失败 HTTP " + code);
        }
        return bos.toByteArray();
    }

    private JSONObject postJson(String url, String apiKey, String jsonBody,
                                int connectTimeoutMs, int readTimeoutMs, boolean async) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(Math.max(3000, connectTimeoutMs));
        conn.setReadTimeout(Math.max(10000, readTimeoutMs));
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        if (async) {
            conn.setRequestProperty("X-DashScope-Async", "enable");
        }
        byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bytes.length);
        OutputStream os = conn.getOutputStream();
        os.write(bytes);
        os.flush();
        os.close();
        return readResponse(conn);
    }

    private JSONObject getJson(String url, String apiKey, int connectTimeoutMs, int readTimeoutMs) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(Math.max(3000, connectTimeoutMs));
        conn.setReadTimeout(Math.max(10000, readTimeoutMs));
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        return readResponse(conn);
    }

    private JSONObject readResponse(HttpURLConnection conn) throws Exception {
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
        JSONObject resp = JSONObject.parseObject(sb.toString());
        if (code >= 400) {
            String msg = resp == null ? sb.toString() : resp.getString("message");
            if (StringUtils.isEmpty(msg) && resp != null && resp.containsKey("error")) {
                msg = String.valueOf(resp.get("error"));
            }
            throw new ServiceException("万相接口 HTTP " + code + "：" + msg);
        }
        return resp;
    }

    private String resolveApiKey() {
        AlbumProperties.PhotoDrawConfig cfg = cfg();
        if (cfg != null && StringUtils.isNotEmpty(cfg.getApiKey())) {
            return cfg.getApiKey().trim();
        }
        AlbumProperties.AiLandmarkConfig ai = albumProperties.getAiLandmark();
        if (ai != null && StringUtils.isNotEmpty(ai.getApiKey())) {
            return ai.getApiKey().trim();
        }
        return "";
    }

    private String apiBase() {
        AlbumProperties.PhotoDrawConfig cfg = cfg();
        String base = cfg == null ? "" : trim(cfg.getBaseUrl());
        if (StringUtils.isEmpty(base)) {
            base = "https://dashscope.aliyuncs.com/api/v1";
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private AlbumProperties.PhotoDrawConfig cfg() {
        return albumProperties == null ? null : albumProperties.getPhotoDraw();
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
