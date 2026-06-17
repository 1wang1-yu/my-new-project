package com.guide.service;

import com.guide.client.EmbeddingClient;
import com.guide.client.LlmClient;
import com.guide.entity.ChatMessage;
import com.guide.entity.Session;
import com.guide.mapper.ChatMessageMapper;
import com.guide.mapper.GuideSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final String SYSTEM = "你是景区导游助手，回答口语化。结尾必须另起一行，严格按格式输出：建议追问：问题A｜问题B｜问题C";

    private final LlmClient llmClient;
    private final EmbeddingClient embeddingClient;
    private final KnowledgeService knowledgeService;
    private final GuideSessionMapper guideSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final AnalyticsService analyticsService;
    private final TransactionTemplate transactionTemplate;

    @Transactional
    public ChatReply chat(Long userId, String sessionId, String userMessage, String inputType) {
        try {
            return doChat(userId, sessionId, userMessage, inputType);
        } catch (Exception e) {
            log.error("ChatService.chat 异常: {}", e.getMessage(), e);
            return new ChatReply(
                    sessionId != null ? sessionId : UUID.randomUUID().toString(),
                    "抱歉，服务暂时不可用，请稍后再试。",
                    "",
                    "calm",
                    List.of("有什么好玩的景点？", "推荐一条游览路线", "附近有什么美食"),
                    List.of());
        }
    }

    /**
     * 流式问答：SetupResult 在调用线程执行，chunk 通过 consumer 实时回调。
     * 返回的 Runnable 由调用方在流结束后执行以持久化消息（必须在事务线程内调用）。
     */
    public SetupResult prepareStream(Long userId, String sessionId, String userMessage, String inputType) {
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
        List<Float> embedding = embeddingClient.embed(userMessage);
        List<List<Float>> queryEmbedding = embedding.isEmpty() ? null : List.of(embedding);
        List<String> ctx = knowledgeService.retrieveContext(userMessage, queryEmbedding, 2);
        String contextBlock = ctx.isEmpty()
                ? ""
                : ctx.stream().map(c -> "- " + c).collect(Collectors.joining("\n"));

        String promptUser = contextBlock.isEmpty()
                ? userMessage + "\n\n请另起一行输出：建议追问：问题1｜问题2｜问题3"
                : "参考：\n" + contextBlock + "\n\n问题：" + userMessage + "\n\n请另起一行输出：建议追问：问题1｜问题2｜问题3";

        return new SetupResult(key, session, userMessage, inputType, promptUser, start, ctx);
    }

    public void streamToConsumer(SetupResult setup, Consumer<String> onChunk) {
        StringBuilder full = new StringBuilder();
        llmClient.chatStream(SYSTEM, setup.promptUser, chunk -> {
            full.append(chunk);
            onChunk.accept(chunk);
        });
        setup.fullResponse = full.toString();
    }

    public ChatReply finalizeStream(SetupResult setup) {
        return transactionTemplate.execute(status -> {
            String rawAnswer = setup.fullResponse != null ? setup.fullResponse : "";
            String answer = extractAnswer(rawAnswer);
            List<String> suggestedQuestions = extractSuggestedQuestions(rawAnswer);
            if (suggestedQuestions.isEmpty()) {
                suggestedQuestions = defaultSuggestedQuestions(setup.userMessage);
            }

            String emotion = inferEmotion(answer);
            int responseMs = (int) Math.max(120,
                    java.time.Duration.between(setup.start, LocalDateTime.now()).toMillis());

            ChatMessage u = new ChatMessage();
            u.setSessionId(setup.session.getId());
            u.setUserId(setup.session.getUserId());
            u.setRole("user");
            u.setInputType(normalizeInputType(setup.inputType));
            u.setContent(setup.userMessage);
            u.setCreateTime(setup.start);
            chatMessageMapper.save(u);

            ChatMessage a = new ChatMessage();
            a.setSessionId(setup.session.getId());
            a.setUserId(setup.session.getUserId());
            a.setRole("assistant");
            a.setInputType("text");
            a.setContent(answer);
            a.setTtsUrl("");
            a.setEmotion(emotion);
            a.setResponseMs(responseMs);
            a.setCreateTime(LocalDateTime.now());
            chatMessageMapper.save(a);

            setup.session.setMsgCount(setup.session.getMsgCount() + 2);
            guideSessionMapper.save(setup.session);

            analyticsService.record("chat",
                    "{\"sessionId\":\"" + setup.key + "\",\"inputType\":\"" + normalizeInputType(setup.inputType) + "\"}");
            return new ChatReply(setup.key, answer, "", emotion, suggestedQuestions, setup.ctx);
        });
    }

    private ChatReply doChat(Long userId, String sessionId, String userMessage, String inputType) {
        SetupResult setup = prepareStream(userId, sessionId, userMessage, inputType);
        streamToConsumer(setup, chunk -> {});
        return finalizeStream(setup);
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

    public static class SetupResult {
        public final String key;
        public final Session session;
        public final String userMessage;
        public final String inputType;
        public final String promptUser;
        public final LocalDateTime start;
        public final List<String> ctx;
        public String fullResponse;

        SetupResult(String key, Session session, String userMessage, String inputType,
                    String promptUser, LocalDateTime start, List<String> ctx) {
            this.key = key;
            this.session = session;
            this.userMessage = userMessage;
            this.inputType = inputType;
            this.promptUser = promptUser;
            this.start = start;
            this.ctx = ctx;
        }
    }

    public record ChatReply(String sessionId, String answer, String ttsUrl, String emotion,
                            List<String> suggestedQuestions, List<String> citations) {
    }
}
