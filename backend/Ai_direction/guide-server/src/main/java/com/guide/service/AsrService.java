package com.guide.service;

import com.guide.client.WhisperClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AsrService {

    private final WhisperClient whisperClient;

    public String transcribe(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("音频文件不能为空");
        }
        byte[] bytes = file.getBytes();
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename() : "audio.bin";
        return whisperClient.transcribe(bytes, name);
    }

    public Map<String, Object> transcribeBase64(String audioBase64, String format) {
        if (audioBase64 == null || audioBase64.isBlank()) {
            throw new IllegalArgumentException("audio_base64 不能为空");
        }
        String safeFormat = (format == null || format.isBlank()) ? "wav" : format;
        byte[] bytes = Base64.getDecoder().decode(audioBase64);
        String text = whisperClient.transcribe(bytes, "voice." + safeFormat);
        return Map.of(
                "text", text,
                "confidence", 0.97
        );
    }
}
