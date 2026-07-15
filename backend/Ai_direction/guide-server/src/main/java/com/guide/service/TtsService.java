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

    private static final String DEFAULT_VOICE_ZH = "101001";
    private static final String DEFAULT_VOICE_EN = "1050";

    public Map<String, Object> synthesize(String text, String voiceId, Double speed, String emotion, String language) {
        boolean en = "en".equalsIgnoreCase(language);
        String safeVoiceId = normalizeVoiceId(voiceId, en);
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
            var result = tencentTtsClient.textToVoiceWithTiming(text, safeVoiceId, safeSpeed, en, safeEmotion);
            String audioBase64 = (String) result.get("audio");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> timings = (List<Map<String, Object>>) result.get("timings");
            // 从字幕时间轴生成口型数据
            List<Map<String, Object>> lipSync = new java.util.ArrayList<>();
            int durationMs = 1800;
            if (timings != null && !timings.isEmpty()) {
                lipSync.add(Map.of("time", 0, "viseme", "rest", "open", 0.05));
                for (var t : timings) {
                    int begin = (int) t.get("begin");
                    int end = (int) t.get("end");
                    if (end > durationMs) durationMs = end;
                    lipSync.add(Map.of("time", begin, "viseme", "A", "open", 0.75));
                    lipSync.add(Map.of("time", end - 20, "viseme", "rest", "open", 0.10));
                }
                lipSync.add(Map.of("time", durationMs + 100, "viseme", "rest", "open", 0.05));
            } else {
                // 无字幕时按字数均分时间
                int len = text != null ? text.length() : 1;
                int perChar = Math.max(80, durationMs / len);
                lipSync.add(Map.of("time", 0, "viseme", "rest", "open", 0.05));
                for (int i = 0; i < len; i++) {
                    int t = (i + 1) * perChar;
                    if (t > durationMs) t = durationMs;
                    String vm = (i % 3 == 0) ? "A" : (i % 3 == 1) ? "O" : "I";
                    double op = (i % 3 == 0) ? 0.75 : (i % 3 == 1) ? 0.50 : 0.30;
                    // 标点闭口
                    if ("，。！？、；： ".indexOf(text.charAt(i)) >= 0) {
                        vm = "rest"; op = 0.05;
                    }
                    lipSync.add(Map.of("time", t, "viseme", vm, "open", op));
                }
                lipSync.add(Map.of("time", durationMs + 100, "viseme", "rest", "open", 0.05));
            }
            return Map.of(
                    "audio_base64", audioBase64,
                    "duration_ms", Math.max(durationMs, 1800),
                    "lip_sync_data", lipSync,
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

    private String normalizeVoiceId(String voiceId, boolean en) {
        String defaultVoice = en ? DEFAULT_VOICE_EN : DEFAULT_VOICE_ZH;
        if (voiceId == null || voiceId.isBlank()) return defaultVoice;
        try {
            Long.parseLong(voiceId);
            return voiceId;
        } catch (NumberFormatException e) {
            return defaultVoice;
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
            result.put("lip_sync_data", generateLipSync(text, text != null ? text.length() * 220 : 2000));
            return result;
        } catch (Exception e) {
            log.error("数智人视频生成失败: {}", e.getMessage());
            return Map.of("error", e.getMessage(), "mode", "video");
        }
    }

    /**
     * 用正弦波生成口型时间轴，模拟自然说话节奏
     * 周期 300~500ms，开度 0.2~0.9，始终微张防止僵硬
     */
    private List<Map<String, Object>> generateLipSync(String text, int durationMs) {
        List<Map<String, Object>> visemes = new java.util.ArrayList<>();
        if (text == null || text.isBlank() || durationMs < 200) return visemes;

        int totalMs = Math.max(durationMs, 500);
        double cycleMs = Math.max(250, Math.min(450, totalMs / Math.max(text.length(), 1) * 1.8));
        double openMin = 0.20;
        double openMax = 0.85;
        int interval = 30;  // 每30ms一个采样点

        for (int t = 0; t <= totalMs + 200; t += interval) {
            double phase = (t / cycleMs) * Math.PI * 2;
            double sinVal = Math.sin(phase);
            // 基线偏移：0.2 + 0.8 * ((sin+1)/2) → 范围 0.2~1.0
            double norm = (sinVal + 1) / 2;  // 0~1
            double open = openMin + (openMax - openMin) * (0.15 + 0.85 * norm);

            // 渐入渐出
            if (t < 300) open *= Math.min(1, t / 300.0 * 1.3);
            else if (t > totalMs - 200) open *= Math.max(0, (totalMs - t) / 200.0 * 2.5);

            visemes.add(Map.of("time", t, "viseme", "A", "open", Math.min(open, 1.0)));
        }
        return visemes;
    }
}
