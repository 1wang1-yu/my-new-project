package com.guide.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guide.config.GuideProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TencentTtsClient {

    private static final String SERVICE = "tts";
    private static final String VERSION = "2019-08-23";
    /** 基础音色单次合成最大字符数 */
    private static final int MAX_CHARS_PER_CHUNK = 150;

    private final GuideProperties guideProperties;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 调用腾讯云 TTS TextToVoice，长文本自动分段合成并拼接。
     */
    public String textToVoice(String text, String voiceType, Double speed) {
        if (text == null || text.isBlank()) {
            throw new RuntimeException("TTS 文本为空");
        }
        List<String> chunks = splitText(text);
        if (chunks.size() == 1) {
            return synthesize(chunks.get(0), voiceType, speed);
        }

        log.info("TTS 文本 {} 字符，拆分为 {} 段", text.length(), chunks.size());
        List<byte[]> mp3Parts = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String b64 = synthesize(chunks.get(i), voiceType, speed);
            mp3Parts.add(Base64.getDecoder().decode(b64));
            if (i < chunks.size() - 1) {
                sleepMs(200);
            }
        }
        byte[] combined = concatByteArrays(mp3Parts);
        return Base64.getEncoder().encodeToString(combined);
    }

    private String synthesize(String text, String voiceType, Double speed) {
        GuideProperties.TencentCloud tc = guideProperties.getTencentCloud();
        String host = tc.getTtsEndpoint();

        try {
            Map<String, Object> body = Map.of(
                    "Text", text,
                    "SessionId", java.util.UUID.randomUUID().toString(),
                    "VoiceType", Integer.parseInt(voiceType != null ? voiceType : "101001"),
                    "PrimaryLanguage", 1,
                    "Speed", speed != null ? speed.floatValue() : 0.0f,
                    "Codec", "mp3",
                    "SampleRate", 16000,
                    "Volume", 5.0f
            );
            String payload = objectMapper.writeValueAsString(body);

            long unixTs = System.currentTimeMillis() / 1000;
            String authorization = TencentCloudSignature.buildAuthorization(
                    tc.getSecretId(), tc.getSecretKey(),
                    SERVICE, host,
                    "TextToVoice", VERSION,
                    tc.getRegion(), payload,
                    unixTs);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://" + host))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("X-TC-Action", "TextToVoice")
                    .header("X-TC-Version", VERSION)
                    .header("X-TC-Timestamp", String.valueOf(unixTs))
                    .header("X-TC-Region", tc.getRegion())
                    .header("Authorization", authorization)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            log.info("TTS 请求: text_len={}", text.length());
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("腾讯云 TTS 请求失败: HTTP {} body={}", response.statusCode(), response.body());
                throw new RuntimeException("腾讯云 TTS 请求失败: HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode resp = root.path("Response");
            if (resp.has("Error")) {
                String code = resp.path("Error").path("Code").asText();
                String msg = resp.path("Error").path("Message").asText();
                log.error("腾讯云 TTS API 错误: {} - {}", code, msg);
                throw new RuntimeException("腾讯云 TTS 错误: " + msg);
            }

            String audio = resp.path("Audio").asText();
            if (audio == null || audio.isBlank()) {
                throw new RuntimeException("腾讯云 TTS 未返回音频数据");
            }
            return audio;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("腾讯云 TTS 调用异常", e);
            throw new RuntimeException("腾讯云 TTS 调用失败: " + e.getMessage(), e);
        }
    }

    /** 按句子边界拆分文本，每段不超过 MAX_CHARS_PER_CHUNK */
    private List<String> splitText(String text) {
        if (text.length() <= MAX_CHARS_PER_CHUNK) {
            return List.of(text);
        }
        List<String> chunks = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            buf.append(c);
            boolean isPunct = "。！？!?\n.；;，,".indexOf(c) >= 0;
            if (isPunct && buf.length() >= MAX_CHARS_PER_CHUNK / 2) {
                chunks.add(buf.toString().trim());
                buf.setLength(0);
            } else if (buf.length() >= MAX_CHARS_PER_CHUNK) {
                chunks.add(buf.toString().trim());
                buf.setLength(0);
            }
        }
        if (!buf.isEmpty()) {
            String last = buf.toString().trim();
            if (!last.isEmpty()) {
                // 最后一段太短就合入前一段；合并后不能超过限制
                if (chunks.size() > 0 && last.length() < 20
                        && chunks.get(chunks.size() - 1).length() + last.length() <= MAX_CHARS_PER_CHUNK) {
                    chunks.set(chunks.size() - 1, chunks.get(chunks.size() - 1) + last);
                } else {
                    chunks.add(last);
                }
            }
        }
        return chunks;
    }

    private static byte[] concatByteArrays(List<byte[]> parts) {
        int total = parts.stream().mapToInt(p -> p.length).sum();
        byte[] out = new byte[total];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }

    private static void sleepMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
