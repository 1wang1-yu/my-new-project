package com.guide.service;

import com.guide.client.TencentIvhClient;
import com.guide.client.TencentTtsClient;
import com.guide.config.GuideProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TtsService {

    private final GuideProperties guideProperties;
    private final TencentTtsClient tencentTtsClient;
    private final TencentIvhClient tencentIvhClient;

    private static final String DEFAULT_VOICE = "101001";

    public Map<String, Object> synthesize(String text, String voiceId, Double speed, String emotion) {
        String safeVoiceId = normalizeVoiceId(voiceId);
        double safeSpeed = speed == null ? 1.0 : speed;
        String safeEmotion = (emotion == null || emotion.isBlank()) ? "calm" : emotion;

        GuideProperties.TencentCloud tc = guideProperties.getTencentCloud();
        if (tc.getSecretId() == null || tc.getSecretId().isBlank()) {
            log.warn("腾讯云密钥未配置，返回占位音频");
            return Map.of(
                    "audio_url", "https://mock.guide.local/audio/not_configured.mp3",
                    "duration_ms", Math.max(1800, text == null ? 1800 : text.length() * 220),
                    "voice_id", safeVoiceId,
                    "speed", safeSpeed,
                    "emotion", safeEmotion
            );
        }

        try {
            String audioBase64 = tencentTtsClient.textToVoice(text, safeVoiceId, safeSpeed);
            return Map.of(
                    "audio_base64", audioBase64,
                    "duration_ms", Math.max(1800, text == null ? 1800 : text.length() * 220),
                    "lip_sync_data", generateDefaultLipSync(text),
                    "voice_id", safeVoiceId,
                    "speed", safeSpeed,
                    "emotion", safeEmotion
            );
        } catch (Exception e) {
            log.error("腾讯云 TTS 合成失败: {}", e.getMessage());
            return Map.of(
                    "audio_url", "https://mock.guide.local/audio/tts_error.mp3",
                    "duration_ms", Math.max(1800, text == null ? 1800 : text.length() * 220),
                    "voice_id", safeVoiceId,
                    "speed", safeSpeed,
                    "emotion", safeEmotion,
                    "error", e.getMessage()
            );
        }
    }

    private String normalizeVoiceId(String voiceId) {
        if (voiceId == null || voiceId.isBlank()) return DEFAULT_VOICE;
        try {
            Long.parseLong(voiceId);
            return voiceId;
        } catch (NumberFormatException e) {
            return DEFAULT_VOICE;
        }
    }

    public Map<String, Object> synthesizeVideo(String text, String virtualmanKey,
                                                String resolution, String bgUrl) {
        GuideProperties.TencentCloud tc = guideProperties.getTencentCloud();
        if (tc.getSecretId() == null || tc.getSecretId().isBlank()) {
            return Map.of("error", "腾讯云密钥未配置");
        }
        try {
            Map<String, Object> result = tencentIvhClient.submitAndWait(
                    text, virtualmanKey, resolution, bgUrl);
            result.put("mode", "video");
            result.put("lip_sync_data", generateDefaultLipSync(text));
            return result;
        } catch (Exception e) {
            log.error("数智人视频生成失败: {}", e.getMessage());
            return Map.of("error", e.getMessage(), "mode", "video");
        }
    }

    private List<Map<String, Object>> generateDefaultLipSync(String text) {
        int len = text == null ? 0 : text.length();
        return List.of(
                Map.of("time", 0, "value", 0.1),
                Map.of("time", len * 110, "value", 0.75),
                Map.of("time", len * 200, "value", 0.28)
        );
    }
}
