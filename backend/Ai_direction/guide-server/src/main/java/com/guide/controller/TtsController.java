package com.guide.controller;

import com.guide.pojo.dto.ApiResponse;
import com.guide.pojo.dto.TtsRequestDTO;
import com.guide.service.TtsService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Api(tags = "游客端语音能力")
@RestController
@RequestMapping("/api/v1/tts")
@RequiredArgsConstructor
public class TtsController {

    private final TtsService ttsService;

    @ApiOperation(value = "TTS 合成", notes = "根据文本、音色、语速和情绪生成音频，mode=video 时生成数智人视频。")
    @PostMapping
    public ApiResponse<Map<String, Object>> synthesize(@Valid @RequestBody TtsRequestDTO req) {
        if ("video".equalsIgnoreCase(req.getMode())) {
            Map<String, Object> params = req.getExtraParams() != null
                    ? req.getExtraParams() : Map.of();
            String vk = safeParam(params, "virtualman_key", "");
            String res = safeParam(params, "resolution", "1080");
            String bg = safeParam(params, "bg_url", "");
            return ApiResponse.ok(ttsService.synthesizeVideo(req.getText(), vk, res, bg));
        }
        return ApiResponse.ok(ttsService.synthesize(
                req.getText(), req.getVoiceId(), req.getSpeed(), req.getEmotion()));
    }

    private static String safeParam(Map<String, Object> params, String key, String def) {
        Object val = params.getOrDefault(key, def);
        return val == null ? def : val.toString();
    }
}
