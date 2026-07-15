package com.guide.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guide.common.exception.BaseException;
import com.guide.config.GuideProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 腾讯云 ASR 语音识别客户端（短语音识别 SentenceRecognition）
 * 支持 wav/mp3 格式，单次最长 60s 音频。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TencentAsrClient {

    private static final String SERVICE = "asr";
    private static final String VERSION = "2019-06-14";
    private static final String HOST = "asr.tencentcloudapi.com";

    private final GuideProperties guideProperties;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 语音识别（短音频，最长 60s）
     * @param audioBytes  音频二进制数据
     * @param filename    文件名（用于判断格式）
     * @return 识别文本
     */
    public String recognize(byte[] audioBytes, String filename) {
        GuideProperties.TencentCloud tc = guideProperties.getTencentCloud();
        String region = tc.getRegion() != null ? tc.getRegion() : "ap-guangzhou";

        // 校验音频数据
        if (audioBytes == null || audioBytes.length == 0) {
            throw new BaseException("音频数据为空");
        }
        if (audioBytes.length < 100) {
            log.warn("音频数据过短: {} bytes, filename={}", audioBytes.length, filename);
        }

        // 判断格式
        String fmt = "wav";
        String name = filename != null ? filename.toLowerCase() : "audio.wav";
        if (name.endsWith(".mp3")) fmt = "mp3";
        else if (name.endsWith(".m4a")) fmt = "m4a";
        else if (name.endsWith(".aac")) fmt = "aac";

        log.info("腾讯云 ASR 请求: format={}, size={}b, filename={}", fmt, audioBytes.length, filename);
        String b64 = Base64.getEncoder().encodeToString(audioBytes);

        try {
            Map<String, Object> bodyMap = new LinkedHashMap<>();
            bodyMap.put("ProjectId", 0);
            bodyMap.put("SubServiceType", 2);
            bodyMap.put("EngSerViceType", "16k_zh");
            bodyMap.put("SourceType", 1);
            bodyMap.put("VoiceFormat", fmt);
            bodyMap.put("Data", b64);
            bodyMap.put("DataLen", audioBytes.length);

            String payload = objectMapper.writeValueAsString(bodyMap);

            long unixTs = System.currentTimeMillis() / 1000;
            String authorization = TencentCloudSignature.buildAuthorization(
                    tc.getSecretId(), tc.getSecretKey(),
                    SERVICE, HOST,
                    "SentenceRecognition", VERSION,
                    region, payload, unixTs);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://" + HOST))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("X-TC-Action", "SentenceRecognition")
                    .header("X-TC-Version", VERSION)
                    .header("X-TC-Timestamp", String.valueOf(unixTs))
                    .header("X-TC-Region", region)
                    .header("Authorization", authorization)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            log.info("腾讯云 ASR 请求: format={}, size={}b", fmt, audioBytes.length);

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("腾讯云 ASR 失败: HTTP {} body={}", response.statusCode(), response.body());
                throw new BaseException("语音识别失败: HTTP " + response.statusCode());
            }

            var root = objectMapper.readTree(response.body());
            var resp = root.path("Response");

            if (resp.has("Error")) {
                String code = resp.path("Error").path("Code").asText();
                String msg = resp.path("Error").path("Message").asText();
                log.error("腾讯云 ASR API 错误: {} - {} (full={})", code, msg, response.body());
                throw new BaseException("语音识别错误: " + msg);
            }

            String result = resp.path("Result").asText("");
            if (!result.isBlank()) {
                log.info("腾讯云 ASR 成功: text={}", result.length() > 60 ? result.substring(0, 60) + "..." : result);
                return result;
            }

            log.warn("腾讯云 ASR 返回结果为空: {}", response.body());
            throw new BaseException("语音识别结果为空");

        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("腾讯云 ASR 异常", e);
            throw new BaseException("语音识别服务暂时不可用");
        }
    }
}
