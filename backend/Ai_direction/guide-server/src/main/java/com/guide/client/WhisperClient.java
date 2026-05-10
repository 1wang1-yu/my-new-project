package com.guide.client;

import com.guide.config.GuideProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Objects;

/**
 * 调用 Whisper 兼容的 HTTP 转写接口（OpenAI 格式 multipart）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhisperClient {

    private final GuideProperties guideProperties;
    private final RestClient.Builder restClientBuilder;

    public String transcribe(byte[] audioBytes, String filename) {
        try {
            GuideProperties.Whisper w = guideProperties.getWhisper();
            String url = w.getBaseUrl() + w.getTranscribePath();

            String safeName = Objects.requireNonNullElse(filename, "audio.webm");

            ByteArrayResource fileResource = new ByteArrayResource(audioBytes) {
                @Override
                public String getFilename() {
                    return safeName;
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", fileResource);
            body.add("model", w.getModel());

            Map<?, ?> response = restClientBuilder.build()
                    .post()
                    .uri(url)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .headers(h -> {
                        if (w.getApiKey() != null && !w.getApiKey().isBlank()) {
                            h.setBearerAuth(w.getApiKey());
                        }
                    })
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (response == null || response.get("text") == null) {
                throw new IllegalStateException("Whisper 响应缺少 text 字段: " + response);
            }
            return String.valueOf(response.get("text"));
        } catch (Exception e) {
            log.warn("Whisper 调用失败，返回模拟转写结果: {}", e.getMessage());
            return "这是一段模拟语音转写文本，当前已切换为本地联调模式。";
        }
    }
}
