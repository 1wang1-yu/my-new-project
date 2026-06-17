package com.guide.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guide.annotation.LogOperation;
import com.guide.pojo.dto.ApiResponse;
import com.guide.pojo.dto.ChatRequestDTO;
import com.guide.service.ChatService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Api(tags = "游客端智能问答")
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @ApiOperation(value = "智能问答", notes = "接收文本或语音转写后的游客问题，返回答案、TTS 地址、情绪和建议追问。")
    @PostMapping
    @LogOperation("chat")
    public ApiResponse<Map<String, Object>> chat(@Valid @RequestBody ChatRequestDTO req) {
        String message = req.getMessage();
        if ((message == null || message.isBlank()) && req.getAudioData() != null && !req.getAudioData().isBlank()) {
            message = "收到一段语音输入，请结合景区知识为游客提供友好讲解。";
        }
        ChatService.ChatReply reply = chatService.chat(
                req.getUserId(),
                req.getSessionId(),
                message,
                req.getInputType()
        );
        return ApiResponse.ok(Map.of(
                "answer", reply.answer(),
                "tts_url", reply.ttsUrl(),
                "emotion", reply.emotion(),
                "suggested_questions", reply.suggestedQuestions(),
                "session_id", reply.sessionId()
        ));
    }

    @ApiOperation(value = "流式智能问答", notes = "SSE 流式返回答案，适合需要即时反馈的场景。")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @LogOperation("chat_stream")
    public SseEmitter chatStream(@Valid @RequestBody ChatRequestDTO req) {
        String message = req.getMessage();
        if ((message == null || message.isBlank()) && req.getAudioData() != null && !req.getAudioData().isBlank()) {
            message = "收到一段语音输入，请结合景区知识为游客提供友好讲解。";
        }
        final String userMessage = message;

        SseEmitter emitter = new SseEmitter(60000L);

        CompletableFuture.runAsync(() -> {
            try {
                ChatService.SetupResult setup = chatService.prepareStream(
                        req.getUserId(), req.getSessionId(), userMessage, req.getInputType());

                chatService.streamToConsumer(setup, chunk -> {
                    try {
                        emitter.send(SseEmitter.event().data(chunk));
                    } catch (IOException e) {
                        throw new RuntimeException("SSE send failed", e);
                    }
                });

                ChatService.ChatReply reply = chatService.finalizeStream(setup);

                Map<String, Object> meta = Map.of(
                        "session_id", reply.sessionId(),
                        "suggested_questions", reply.suggestedQuestions()
                );
                emitter.send(SseEmitter.event().name("done").data(objectMapper.writeValueAsString(meta)));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
