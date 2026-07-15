package com.guide.service;

import com.guide.client.TencentAsrClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsrService {

    private final TencentAsrClient tencentAsrClient;

    public String transcribe(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("音频文件不能为空");
        }
        byte[] bytes = file.getBytes();
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename() : "audio.bin";
        return tencentAsrClient.recognize(bytes, name);
    }

    public Map<String, Object> transcribeBase64(String audioBase64, String format) {
        if (audioBase64 == null || audioBase64.isBlank()) {
            throw new IllegalArgumentException("audio_base64 不能为空");
        }
        String safeFormat = (format == null || format.isBlank()) ? "wav" : format;
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(audioBase64);
        } catch (IllegalArgumentException e) {
            log.error("Base64 解码失败, 前50字符: {}", audioBase64.substring(0, Math.min(50, audioBase64.length())));
            throw new IllegalArgumentException("音频数据格式错误，请重新录音");
        }
        if (bytes.length == 0) {
            throw new IllegalArgumentException("音频数据为空");
        }
        log.info("ASR 请求: format={}, base64_len={}, decoded_len={}", safeFormat, audioBase64.length(), bytes.length);
        String text = tencentAsrClient.recognize(bytes, "voice." + safeFormat);
        return Map.of(
                "text", text,
                "confidence", 0.97
        );
    }
}
