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

    @ApiOperation(value = "TTS 合成", notes = "根据文本、音色、语速和情绪生成可播放音频地址与口型数据。")
    @PostMapping
    public ApiResponse<Map<String, Object>> synthesize(@Valid @RequestBody TtsRequestDTO req) {
        return ApiResponse.ok(ttsService.synthesize(req.getText(), req.getVoiceId(), req.getSpeed(), req.getEmotion()));
    }
}
