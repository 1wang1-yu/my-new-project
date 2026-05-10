package com.guide.controller;

import com.guide.annotation.LogOperation;
import com.guide.pojo.dto.ApiResponse;
import com.guide.pojo.dto.AsrRequestDTO;
import com.guide.service.AnalyticsService;
import com.guide.service.AsrService;
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
@RequestMapping("/api/v1/asr")
@RequiredArgsConstructor
public class AsrController {

    private final AsrService asrService;
    private final AnalyticsService analyticsService;

    @ApiOperation(value = "语音转文字", notes = "接收 Base64 音频并调用 ASR 服务返回文本和置信度。")
    @PostMapping
    @LogOperation("asr")
    public ApiResponse<Map<String, Object>> transcribe(@Valid @RequestBody AsrRequestDTO req) {
        Map<String, Object> result = asrService.transcribeBase64(req.getAudioBase64(), req.getFormat());
        analyticsService.record("asr", "{}");
        return ApiResponse.ok(result);
    }
}
