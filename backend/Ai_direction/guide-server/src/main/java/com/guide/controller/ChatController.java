package com.guide.controller;

import com.guide.annotation.LogOperation;
import com.guide.pojo.dto.ApiResponse;
import com.guide.pojo.dto.ChatRequestDTO;
import com.guide.service.ChatService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Api(tags = "游客端智能问答")
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

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
}
