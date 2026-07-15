package com.guide.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.guide.common.exception.BaseException;
import com.guide.config.GuideProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 语音识别客户端 — 支持两种模式：
 * 1. 兼容模式（base-url含 compatible-mode）→ multipart上传，直接返回文本
 * 2. 原生模式（不含 compatible-mode）→ 异步提交 + 轮询
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhisperClient {

    private final GuideProperties guideProperties;
    private final RestClient.Builder restClientBuilder;

    public String transcribe(byte[] audioBytes, String filename) {
        GuideProperties.Whisper w = guideProperties.getWhisper();
        String baseUrl = w.getBaseUrl();
        String modelName = (w.getModel() == null || w.getModel().isBlank()) ? "whisper-1" : w.getModel();
        String transcribePath = (w.getTranscribePath() != null && !w.getTranscribePath().isBlank())
                ? w.getTranscribePath() : "/audio/transcriptions";
        String submitUrl = baseUrl + transcribePath;

        // 统一转为 wav 后缀
        String rawName = Objects.requireNonNullElse(filename, "audio.wav");
        if (rawName.endsWith(".mp3")) rawName = rawName.replace(".mp3", ".wav");
        final String safeName = rawName;

        // multipart 上传
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("model", modelName);
        body.add("file", new ByteArrayResource(audioBytes) {
            @Override public String getFilename() { return safeName; }
        });

        log.info("ASR 请求: model={}, url={}, file={} ({} bytes)", modelName, submitUrl, safeName, audioBytes.length);

        try {
            JsonNode resp = restClientBuilder.build()
                    .post()
                    .uri(submitUrl)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .headers(h -> {
                        if (w.getApiKey() != null && !w.getApiKey().isBlank()) h.setBearerAuth(w.getApiKey());
                    })
                    .body(body)
                    .retrieve()
                    .onStatus(status -> status.isError(), (req, res) -> {
                        String errBody = readResponseBody(res);
                        log.error("ASR 失败: HTTP {} body={}", res.getStatusCode(), errBody);
                        throw new BaseException("语音识别失败: HTTP " + res.getStatusCode());
                    })
                    .body(JsonNode.class);

            // 兼容模式：直接返回 { "text": "..." }
            if (resp != null && resp.has("text")) {
                String text = resp.get("text").asText("");
                if (!text.isBlank()) {
                    log.info("ASR 成功: text={}", text.length() > 60 ? text.substring(0, 60) + "..." : text);
                    return text;
                }
            }
            // 原生模式异步：找 task_id
            String taskId = resp != null ? resp.path("output").path("task_id").asText("") : "";
            if (!taskId.isBlank()) {
                return pollResult(taskId, w.getApiKey());
            }
            log.error("ASR 响应异常: {}", resp);
            throw new BaseException("语音识别响应格式异常");
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("ASR 异常: {}", e.getMessage(), e);
            throw new BaseException("语音识别服务暂时不可用");
        }
    }

    private String pollResult(String taskId, String apiKey) throws Exception {
        String pollUrl = "https://dashscope.aliyuncs.com/api/v1/tasks/" + taskId;
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(1000);
            JsonNode resp = restClientBuilder.build()
                    .get().uri(pollUrl)
                    .headers(h -> { if (apiKey != null && !apiKey.isBlank()) h.setBearerAuth(apiKey); })
                    .retrieve().body(JsonNode.class);
            if (resp == null) continue;
            String status = resp.path("output").path("task_status").asText("");
            if ("SUCCEEDED".equals(status)) {
                String text = extractText(resp);
                if (!text.isBlank()) return text;
            } else if ("FAILED".equals(status)) {
                throw new BaseException("语音识别失败: " + resp.path("output").path("message").asText());
            }
        }
        throw new BaseException("语音识别超时");
    }

    private String extractText(JsonNode node) {
        if (node == null) return "";
        String t = node.path("output").path("text").asText("");
        if (!t.isBlank()) return t;
        t = node.path("text").asText("");
        if (!t.isBlank()) return t;
        JsonNode results = node.path("output").path("results");
        if (results.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode r : results) {
                String s = r.path("text").asText("");
                if (!s.isBlank()) sb.append(sb.length() > 0 ? " " : "").append(s);
            }
            return sb.toString();
        }
        return "";
    }

    private String readResponseBody(ClientHttpResponse res) {
        try {
            return new BufferedReader(new InputStreamReader(res.getBody(), StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "(读取失败)";
        }
    }
}
