package com.guide.service;

import com.guide.client.LlmClient;
import com.guide.entity.ChatMessage;
import com.guide.entity.Session;
import com.guide.mapper.ChatMessageMapper;
import com.guide.mapper.GuideSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final String SYSTEM = "你是智能旅游导游助手，结合知识库片段回答用户，无法确定时请说明并给出建议。回答尽量口语化、易懂，并在结尾给出 3 个可继续追问的问题。";

    private final LlmClient llmClient;
    private final KnowledgeService knowledgeService;
    private final GuideSessionMapper guideSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final TtsService ttsService;
    private final AnalyticsService analyticsService;

    @Transactional
    public ChatReply chat(Long userId, String sessionId, String userMessage, String inputType) {
        String key = (sessionId == null || sessionId.isBlank()) ? UUID.randomUUID().toString() : sessionId;
        Session session = guideSessionMapper.findBySessionKey(key)
                .orElseGet(() -> {
                    Session s = new Session();
                    s.setSessionKey(key);
                    s.setUserId(userId);
                    s.setStartTime(LocalDateTime.now());
                    s.setMsgCount(0);
                    s.setStatus(1);
                    return guideSessionMapper.save(s);
                });

        LocalDateTime start = LocalDateTime.now();
        List<String> ctx = knowledgeService.retrieveContext(userMessage, null, 4);
        String contextBlock = ctx.isEmpty()
                ? "（暂无检索到的知识库片段）"
                : ctx.stream().map(c -> "- " + c).collect(Collectors.joining("\n"));

        String promptUser = "用户问题：\n" + userMessage + "\n\n知识库参考：\n" + contextBlock
                + "\n\n请额外输出一行：建议追问：问题1｜问题2｜问题3";
        String rawAnswer = llmClient.chat(SYSTEM, promptUser);
        String answer = extractAnswer(rawAnswer);
        List<String> suggestedQuestions = extractSuggestedQuestions(rawAnswer);
        if (suggestedQuestions.isEmpty()) {
            suggestedQuestions = defaultSuggestedQuestions(userMessage);
        }

        Map<String, Object> ttsResult = ttsService.synthesize(answer, "guide-default", 1.0, inferEmotion(answer));
        String ttsUrl = String.valueOf(ttsResult.getOrDefault("audio_url", ""));
        String emotion = String.valueOf(ttsResult.getOrDefault("emotion", inferEmotion(answer)));
        int responseMs = Math.max(120, java.time.Duration.between(start, LocalDateTime.now()).toMillisPart());

        ChatMessage u = new ChatMessage();
        u.setSessionId(session.getId());
        u.setUserId(userId);
        u.setRole("user");
        u.setInputType(normalizeInputType(inputType));
        u.setContent(userMessage);
        u.setCreateTime(start);
        chatMessageMapper.save(u);

        ChatMessage a = new ChatMessage();
        a.setSessionId(session.getId());
        a.setUserId(userId);
        a.setRole("assistant");
        a.setInputType("text");
        a.setContent(answer);
        a.setTtsUrl(ttsUrl);
        a.setEmotion(emotion);
        a.setResponseMs(responseMs);
        a.setCreateTime(LocalDateTime.now());
        chatMessageMapper.save(a);

        session.setMsgCount(session.getMsgCount() + 2);
        guideSessionMapper.save(session);

        analyticsService.record("chat", "{\"sessionId\":\"" + key + "\",\"inputType\":\"" + normalizeInputType(inputType) + "\"}");
        return new ChatReply(key, answer, ttsUrl, emotion, suggestedQuestions, ctx);
    }

    private String normalizeInputType(String inputType) {
        return "voice".equalsIgnoreCase(inputType) ? "voice" : "text";
    }

    private String extractAnswer(String rawAnswer) {
        if (rawAnswer == null || rawAnswer.isBlank()) {
            return "抱歉，我暂时没能生成回答，你可以换个问法再试试。";
        }
        int marker = rawAnswer.indexOf("建议追问：");
        return marker >= 0 ? rawAnswer.substring(0, marker).trim() : rawAnswer.trim();
    }

    private List<String> extractSuggestedQuestions(String rawAnswer) {
        if (rawAnswer == null) {
            return List.of();
        }
        int marker = rawAnswer.indexOf("建议追问：");
        if (marker < 0) {
            return List.of();
        }
        String suffix = rawAnswer.substring(marker + "建议追问：".length()).trim();
        return java.util.Arrays.stream(suffix.split("[｜|]"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .limit(3)
                .toList();
    }

    private List<String> defaultSuggestedQuestions(String userMessage) {
        return List.of(
                "这个景点什么时候去最合适？",
                "附近还有什么值得顺路打卡的地方？",
                "能帮我安排一条更轻松的游览路线吗？"
        );
    }

    private String inferEmotion(String answer) {
        String text = answer == null ? "" : answer.toLowerCase(Locale.ROOT);
        if (text.contains("推荐") || text.contains("值得") || text.contains("非常适合")) {
            return "positive";
        }
        if (text.contains("抱歉") || text.contains("不建议") || text.contains("无法确定")) {
            return "neutral";
        }
        return "calm";
    }

    public record ChatReply(String sessionId, String answer, String ttsUrl, String emotion,
                            List<String> suggestedQuestions, List<String> citations) {
    }
}
