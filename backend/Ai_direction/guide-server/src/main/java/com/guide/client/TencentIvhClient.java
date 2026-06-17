package com.guide.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guide.config.GuideProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 腾讯云数智人（IVH）aPaas 视频生成客户端
 * 鉴权方式：query-param signature（appkey + timestamp + HMAC-SHA256）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TencentIvhClient {

    private static final String BASE_URL = "https://gw.tvs.qq.com";
    private static final String SUBMIT_PATH = "/v2/ivh/videomaker/broadcastservice/videomake";
    private static final String GET_PROGRESS_PATH = "/v2/ivh/videomaker/broadcastservice/getprogress";
    private static final int POLL_INTERVAL_MS = 3000;
    private static final int MAX_POLL_SECONDS = 120;

    private final GuideProperties guideProperties;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> submitAndWait(String text, String virtualmanKey,
                                              String resolution, String bgUrl) {
        GuideProperties.TencentCloud tc = guideProperties.getTencentCloud();
        String safeVk = (virtualmanKey == null || virtualmanKey.isBlank())
                ? tc.getVirtualmanKey() : virtualmanKey;

        if (safeVk == null || safeVk.isBlank()) {
            throw new RuntimeException("未配置数智人形象 Key (virtualman-key)");
        }
        if (tc.getAppKey() == null || tc.getAppKey().isBlank()
                || tc.getAccessToken() == null || tc.getAccessToken().isBlank()) {
            throw new RuntimeException("未配置数智人 AppKey/AccessToken");
        }

        // 1) 提交任务
        String taskId;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("VirtualmanKey", safeVk);
            payload.put("InputSsml", "<speak>" + text + "</speak>");
            payload.put("DriverType", "Text");

            Map<String, Object> speechParam = new LinkedHashMap<>();
            speechParam.put("Speed", 1.0);
            payload.put("SpeechParam", speechParam);

            Map<String, Object> videoParam = new LinkedHashMap<>();
            String fmt = (resolution != null && resolution.contains("720")) ? "Mp4" : "Mp4";
            videoParam.put("Format", fmt);
            if (bgUrl != null && !bgUrl.isBlank()) {
                videoParam.put("BackgroundFileUrl", bgUrl);
            }
            payload.put("VideoParam", videoParam);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("Header", Map.of());
            body.put("Payload", payload);

            JsonNode resp = callIvhApi(SUBMIT_PATH, tc, body);
            JsonNode payloadResp = resp.path("Payload");
            taskId = payloadResp.path("TaskID").asText();
            if (taskId.isBlank()) {
                taskId = payloadResp.path("TaskId").asText();
            }
            if (taskId.isBlank()) {
                throw new RuntimeException("提交数智人任务失败: 未返回 TaskID, body=" + resp.toPrettyString());
            }
            log.info("数智人任务已提交: taskId={}", taskId);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("提交数智人任务异常: " + e.getMessage(), e);
        }

        // 2) 轮询直到完成
        long deadline = System.currentTimeMillis() + MAX_POLL_SECONDS * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("轮询被中断", e);
            }
            try {
                Map<String, Object> queryBody = new LinkedHashMap<>();
                queryBody.put("Header", Map.of());
                queryBody.put("Payload", Map.of("TaskId", taskId));

                JsonNode resp = callIvhApi(GET_PROGRESS_PATH, tc, queryBody);
                JsonNode payloadResp = resp.path("Payload");
                int progress = payloadResp.path("Progress").asInt(-2);
                String status = payloadResp.path("Status").asText();
                log.info("数智人任务状态: taskId={} progress={} status={}", taskId, progress, status);

                if (progress == 100 || "SUCCESS".equalsIgnoreCase(status)) {
                    String videoUrl = payloadResp.path("MediaUrl").asText();
                    if (videoUrl.isBlank()) {
                        videoUrl = payloadResp.path("VideoUrl").asText();
                    }
                    if (videoUrl.isBlank()) {
                        throw new RuntimeException("任务完成但未返回视频 URL: " + resp.toPrettyString());
                    }
                    log.info("数智人视频已生成: {}", videoUrl);
                    return Map.of(
                            "video_url", videoUrl,
                            "task_id", taskId,
                            "duration_ms", payloadResp.path("Duration").asInt(0)
                    );
                }
                if (progress == -1 || "FAIL".equalsIgnoreCase(status)) {
                    String errMsg = payloadResp.path("FailMessage").asText("未知错误");
                    throw new RuntimeException("数智人视频生成失败: " + errMsg);
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                log.warn("查询数智人任务状态异常: {}", e.getMessage());
            }
        }
        throw new RuntimeException("数智人视频生成超时（>" + MAX_POLL_SECONDS + "s）");
    }

    private JsonNode callIvhApi(String path, GuideProperties.TencentCloud tc,
                                 Map<String, Object> body) throws Exception {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String signStr = "appkey=" + tc.getAppKey() + "&timestamp=" + timestamp;
        String signature = hmacSha256Base64(signStr, tc.getAccessToken());
        String encodedSig = URLEncoder.encode(signature, StandardCharsets.UTF_8);

        String url = BASE_URL + path + "?appkey=" + tc.getAppKey()
                + "&timestamp=" + timestamp + "&signature=" + encodedSig;

        String payload = objectMapper.writeValueAsString(body);
        log.info("IVH request: url={}", url);
        log.info("IVH body: {}", payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json;charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("IVH response: HTTP {} body={}", response.statusCode(), response.body());

        if (response.statusCode() != 200) {
            throw new RuntimeException("腾讯云 IVH 请求失败: HTTP " + response.statusCode()
                    + " body=" + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode header = root.path("Header");
        int code = header.path("Code").asInt(0);
        if (code != 0) {
            String msg = header.path("Message").asText("未知错误");
            log.error("腾讯云 IVH API 错误: code={} msg={}", code, msg);
            throw new RuntimeException("腾讯云 IVH 错误: " + code + " - " + msg);
        }
        return root;
    }

    static String hmacSha256Base64(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec spec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(spec);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }
}
