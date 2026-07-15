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
    public String textToVoice(String text, String voiceType, Double speed, boolean en) {
        return textToVoice(text, voiceType, speed, en, null);
    }

    public String textToVoice(String text, String voiceType, Double speed, boolean en, String emotion) {
        if (text == null || text.isBlank()) {
            throw new RuntimeException("TTS 文本为空");
        }
        List<String> chunks = splitText(text);
        if (chunks.size() == 1) {
            return synthesize(chunks.get(0), voiceType, speed, en, emotion);
        }

        log.info("TTS 文本 {} 字符，拆分为 {} 段", text.length(), chunks.size());
        List<byte[]> mp3Parts = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String b64 = synthesize(chunks.get(i), voiceType, speed, en, emotion);
            mp3Parts.add(Base64.getDecoder().decode(b64));
            if (i < chunks.size() - 1) {
                sleepMs(200);
            }
        }
        byte[] combined = concatByteArrays(mp3Parts);
        return Base64.getEncoder().encodeToString(combined);
    }

    /**
     * 合成语音，返回音频 base64 + 字幕时间轴
     */
    public Map<String, Object> textToVoiceWithTiming(String text, String voiceType, Double speed, boolean en, String emotion) {
        List<String> chunks = splitText(text);
        List<byte[]> audioParts = new ArrayList<>();
        List<Map<String, Object>> allTimings = new ArrayList<>();
        int timeOffset = 0;

        for (int i = 0; i < chunks.size(); i++) {
            var result = synthesizeWithSubtitle(chunks.get(i), voiceType, speed, en, emotion);
            audioParts.add(Base64.getDecoder().decode((String) result.get("audio")));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> timings = (List<Map<String, Object>>) result.get("timings");
            for (var t : timings) {
                int begin = (int) t.get("begin") + timeOffset;
                int end = (int) t.get("end") + timeOffset;
                t.put("begin", begin);
                t.put("end", end);
                allTimings.add(t);
            }
            // 用音频实际时长作为偏移
            timeOffset += ((int) result.get("duration"));
            if (i < chunks.size() - 1) sleepMs(200);
        }

        byte[] combined = concatByteArrays(audioParts);
        return Map.of(
                "audio", Base64.getEncoder().encodeToString(combined),
                "timings", allTimings
        );
    }

    /** 单段合成：返回 audio base64 + timing 数组 + duration(ms) */
    private Map<String, Object> synthesizeWithSubtitle(String text, String voiceType, Double speed, boolean en, String emotion) {
        GuideProperties.TencentCloud tc = guideProperties.getTencentCloud();
        String host = tc.getTtsEndpoint();

        try {
            var bodyMap = new java.util.LinkedHashMap<String, Object>();
            bodyMap.put("Text", text);
            bodyMap.put("SessionId", java.util.UUID.randomUUID().toString());
            bodyMap.put("VoiceType", Integer.parseInt(voiceType != null ? voiceType : (en ? "1050" : "101001")));
            bodyMap.put("PrimaryLanguage", en ? 2 : 1);
            bodyMap.put("Speed", speed != null ? speed.floatValue() : 0.0f);
            bodyMap.put("Codec", "mp3");
            bodyMap.put("SampleRate", 16000);
            bodyMap.put("Volume", 5.0f);
            if (emotion != null && !"calm".equals(emotion)) {
                bodyMap.put("EmotionCategory", emotion);
            }
            Map<String, Object> body = bodyMap;
            String payload = objectMapper.writeValueAsString(body);

            long unixTs = System.currentTimeMillis() / 1000;
            String authorization = TencentCloudSignature.buildAuthorization(
                    tc.getSecretId(), tc.getSecretKey(),
                    SERVICE, host,
                    "TextToVoice", VERSION,
                    tc.getRegion(), payload, unixTs);

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

            log.info("TTS 请求(含字幕): text_len={}", text.length());
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

            // 解析字幕时间轴
            List<Map<String, Object>> timings = new ArrayList<>();
            JsonNode subtitle = resp.path("Subtitle");
            if (subtitle.isArray()) {
                for (JsonNode s : subtitle) {
                    Map<String, Object> item = new java.util.LinkedHashMap<>();
                    item.put("word", s.path("Text").asText(""));
                    item.put("begin", s.path("BeginTime").asInt(0));
                    item.put("end", s.path("EndTime").asInt(0));
                    timings.add(item);
                }
            }

            int duration = resp.path("AudioDuration").asInt(0);
            return Map.of("audio", audio, "timings", timings, "duration", duration);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("腾讯云 TTS 调用异常", e);
            throw new RuntimeException("腾讯云 TTS 调用失败: " + e.getMessage(), e);
        }
    }

    /** 保留原 synthesize 方法不变 */
    private String synthesize(String text, String voiceType, Double speed, boolean en, String emotion) {
        GuideProperties.TencentCloud tc = guideProperties.getTencentCloud();
        String host = tc.getTtsEndpoint();

        try {
            var bodyMap = new java.util.LinkedHashMap<String, Object>();
            bodyMap.put("Text", text);
            bodyMap.put("SessionId", java.util.UUID.randomUUID().toString());
            bodyMap.put("VoiceType", Integer.parseInt(voiceType != null ? voiceType : (en ? "1050" : "101001")));
            bodyMap.put("PrimaryLanguage", en ? 2 : 1);
            bodyMap.put("Speed", speed != null ? speed.floatValue() : 0.0f);
            bodyMap.put("Codec", "mp3");
            bodyMap.put("SampleRate", 16000);
            bodyMap.put("Volume", 5.0f);
            if (emotion != null && !"calm".equals(emotion)) {
                bodyMap.put("EmotionCategory", emotion);
            }
            Map<String, Object> body = bodyMap;
            String payload = objectMapper.writeValueAsString(body);

            long unixTs = System.currentTimeMillis() / 1000;
            String authorization = TencentCloudSignature.buildAuthorization(
                    tc.getSecretId(), tc.getSecretKey(),
                    SERVICE, host,
                    "TextToVoice", VERSION,
                    tc.getRegion(), payload, unixTs);

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
