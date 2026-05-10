package com.guide.service;

import com.guide.config.GuideProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TTS 占位：当未配置 guide.tts.base-url 时返回可联调的模拟结果；接入具体厂商时在此发起 HTTP 调用。
 */
@Service
@RequiredArgsConstructor
public class TtsService {

    private final GuideProperties guideProperties;

    public Map<String, Object> synthesize(String text, String voiceId, Double speed, String emotion) {
        String safeVoiceId = (voiceId == null || voiceId.isBlank()) ? "guide-default" : voiceId;
        double safeSpeed = speed == null ? 1.0 : speed;
        String safeEmotion = (emotion == null || emotion.isBlank()) ? "calm" : emotion;
        String base = guideProperties.getTts().getBaseUrl();
        String audioUrl = (base == null || base.isBlank())
                ? "https://mock.guide.local/audio/" + UUID.randomUUID() + ".mp3"
                : base.replaceAll("/$", "") + "/preview/" + UUID.randomUUID() + ".mp3";

        return Map.of(
                "audio_url", audioUrl,
                "duration_ms", Math.max(1800, text == null ? 1800 : text.length() * 220),
                "lip_sync_data", List.of(
                        Map.of("time", 0, "value", 0.1),
                        Map.of("time", 180, "value", 0.75),
                        Map.of("time", 320, "value", 0.28)
                ),
                "voice_id", safeVoiceId,
                "speed", safeSpeed,
                "emotion", safeEmotion
        );
    }
}
