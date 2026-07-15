package com.guide.service;

import com.guide.client.EmbeddingClient;
import com.guide.client.LlmClient;
import com.guide.entity.ChatMessage;
import com.guide.entity.Session;
import com.guide.entity.ScenicSpot;
import com.guide.mapper.ChatMessageMapper;
import com.guide.mapper.GuideSessionMapper;
import com.guide.mapper.ScenicSpotMapper;
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

    private static final String SYSTEM_ZH = "你是景区导游助手，基于景区知识库为游客提供生动有感染力的讲解。回答必须严格遵循以下规则：\n\n1. 用户消息中的参考内容（从「参考：」开始的部分）是从景区知识库中检索到的相关信息，你**只能基于这些参考内容来回答**，不得自己编造信息。\n2. 如果参考内容不足以回答问题，如实说「抱歉，知识库中没有相关信息」。\n3. **语气要有感情和表现力**：描述美景时要让游客感受到身临其境，讲历史故事时要娓娓道来，推荐路线时要亲切贴心。每句话都要有情绪起伏和画面感。\n4. **适当使用感叹句和语气词**（哇！太美了！真的很壮观！一定要去！），让回答听起来像真人导游在说话。\n5. **使用丰富的表情符号**（😊🏔️🌸✨🎉👍🌟🔥💫🏯⛩️🌸🌊☀️等），至少用 2-3 个，放在关键位置增强表现力。\n6. **内容要丰富充实**：回答包含 3-5 句话，围绕核心景点给出具体的描写、亮点或推荐理由，不要只说一句干巴巴的话。\n7. 语音播报友好，适合朗读，但不要牺牲情感表达。\n8. **根据游客信息个性化回答**：用户消息开头会包含「游客信息：X岁，男性/女性，来自XX」的描述，你需要根据游客的年龄、性别和所在地调整回答的语气和推荐重点。例如年轻人推荐网红打卡点、拍照点，家庭游客推荐亲子项目和轻松路线，年长者推荐文化内涵丰富的景点和休息区。\n\n结尾必须另起一行，严格按格式输出：建议追问：问题A｜问题B｜问题C。";
    private static final String SYSTEM_EN = "You are a scenic spot tour guide that gives vivid, emotional explanations based on the scenic spot knowledge base. You must strictly follow these rules:\n\n1. The reference content (starting from 'Reference:') in the user message is retrieved from our scenic spot knowledge base. You MUST answer ONLY based on this reference content. Do NOT make up information.\n2. If the reference content is insufficient, honestly say 'Sorry, there is no relevant information in the knowledge base.'\n3. **Be expressive and passionate**: Make the visitor feel like they're there. Use vivid descriptions, emotional tones, and vary your delivery. Be excited when describing beautiful scenery, warm when sharing history.\n4. **Use exclamations and emotional phrases** (Amazing! Stunning! You must see this! Wow!), like a real tour guide speaking.\n5. **Use rich emojis** (😊🏔️🌸✨🎉👍🌟🔥💫🏯⛩️🌸🌊☀️), at least 2-3 per response, placed at key moments to enhance expression.\n6. **Rich content**: 3-5 sentences per response, with specific descriptions, highlights, and recommendations. Don't give dry one-liners.\n7. The answer will be read via TTS, so keep it suitable for voice but don't sacrifice emotion.\n8. **Personalize based on visitor profile**: The user message starts with 'Visitor profile: X years old, male/female, from XX'. Tailor your tone and recommendations based on their age, gender, and location. Recommend trendy spots for young visitors, family-friendly activities for families, and culturally rich spots with rest areas for elderly visitors.\n\nAt the very end, write 3 suggested follow-up questions on a separate line in this exact format: Suggested: <question 1> | <question 2> | <question 3>.";

    private final LlmClient llmClient;
    private final EmbeddingClient embeddingClient;
    private final KnowledgeService knowledgeService;
    private final GuideSessionMapper guideSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ScenicSpotMapper scenicSpotMapper;
    private final AnalyticsService analyticsService;
    private final TransactionTemplate transactionTemplate;

    @Transactional
    public ChatReply chat(Long userId, String sessionId, String userMessage, String inputType, String language,
                          Integer age, String gender, String location,
                          Double latitude, Double longitude) {
        try {
            return doChat(userId, sessionId, userMessage, inputType, language, age, gender, location, latitude, longitude);
        } catch (Exception e) {
            log.error("ChatService.chat 异常: {}", e.getMessage(), e);
            boolean en = "en".equalsIgnoreCase(language);
            return new ChatReply(
                    sessionId != null ? sessionId : UUID.randomUUID().toString(),
                    en ? "Sorry, the service is temporarily unavailable. Please try again later." : "抱歉，服务暂时不可用，请稍后再试。",
                    "",
                    "calm",
                    en ? List.of("What are the best attractions?", "Recommend a tour route", "Any good food nearby?")
                       : List.of("有什么好玩的景点？", "推荐一条游览路线", "附近有什么美食"),
                    List.of());
        }
    }

    /**
     * 流式问答：SetupResult 在调用线程执行，chunk 通过 consumer 实时回调。
     * 返回的 Runnable 由调用方在流结束后执行以持久化消息（必须在事务线程内调用）。
     */
    public SetupResult prepareStream(Long userId, String sessionId, String userMessage, String inputType, String language,
                                     Integer age, String gender, String location,
                                     Double latitude, Double longitude) {
        boolean en = "en".equalsIgnoreCase(language);
        String isEn = en ? "en" : "zh";

        // 构建游客画像描述（包含 GPS 位置附近景点）
        String profileDesc = buildProfileDesc(age, gender, location, en);
        String gpsContext = buildGpsContext(latitude, longitude, en);

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

        // 如果有 GPS 位置信息，添加到 prompt
        String userQuery = userMessage;
        if (!gpsContext.isBlank()) {
            userQuery = gpsContext + "\n\n" + userMessage;
        }

        String promptUser;
        if (en) {
            promptUser = contextBlock.isEmpty()
                    ? profileDesc + userQuery + "\n\nEnd your reply with a line: Suggested: <question 1> | <question 2> | <question 3>"
                    : profileDesc + "Reference:\n" + contextBlock + "\n\nQuestion: " + userQuery + "\n\nEnd your reply with a line: Suggested: <question 1> | <question 2> | <question 3>";
        } else {
            promptUser = contextBlock.isEmpty()
                    ? profileDesc + userQuery + "\n\n请另起一行输出：建议追问：问题1｜问题2｜问题3"
                    : profileDesc + "参考：\n" + contextBlock + "\n\n问题：" + userQuery + "\n\n请另起一行输出：建议追问：问题1｜问题2｜问题3";
        }

        return new SetupResult(key, session, userMessage, inputType, promptUser, start, ctx, isEn);
    }

    public void streamToConsumer(SetupResult setup, Consumer<String> onChunk) {
        StringBuilder full = new StringBuilder();
        String systemPrompt = setup.isEn ? SYSTEM_EN : SYSTEM_ZH;
        llmClient.chatStream(systemPrompt, setup.promptUser, chunk -> {
            full.append(chunk);
            onChunk.accept(chunk);
        });
        setup.fullResponse = full.toString();
    }

    public ChatReply finalizeStream(SetupResult setup) {
        return transactionTemplate.execute(status -> {
            String rawAnswer = setup.fullResponse != null ? setup.fullResponse : "";
            String answer = extractAnswer(rawAnswer, setup.isEn);
            List<String> suggestedQuestions = extractSuggestedQuestions(rawAnswer, setup.isEn);
            if (suggestedQuestions.isEmpty()) {
                suggestedQuestions = defaultSuggestedQuestions(setup.userMessage, setup.isEn);
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

    private ChatReply doChat(Long userId, String sessionId, String userMessage, String inputType, String language,
                             Integer age, String gender, String location,
                             Double latitude, Double longitude) {
        SetupResult setup = prepareStream(userId, sessionId, userMessage, inputType, language, age, gender, location, latitude, longitude);
        streamToConsumer(setup, chunk -> {});
        return finalizeStream(setup);
    }

    private String normalizeInputType(String inputType) {
        return "voice".equalsIgnoreCase(inputType) ? "voice" : "text";
    }

    private String extractAnswer(String rawAnswer, boolean en) {
        if (rawAnswer == null || rawAnswer.isBlank()) {
            return en ? "Sorry, I couldn't generate a response. Please try a different question."
                      : "抱歉，我暂时没能生成回答，你可以换个问法再试试。";
        }
        String suffixMarker = en ? "Suggested:" : "建议追问：";
        int marker = rawAnswer.indexOf(suffixMarker);
        return marker >= 0 ? rawAnswer.substring(0, marker).trim() : rawAnswer.trim();
    }

    private List<String> extractSuggestedQuestions(String rawAnswer, boolean en) {
        if (rawAnswer == null) {
            return List.of();
        }
        String suffixMarker = en ? "Suggested:" : "建议追问：";
        int marker = rawAnswer.indexOf(suffixMarker);
        if (marker < 0) {
            // Also try the other language's marker as fallback
            String fallback = en ? "建议追问：" : "Suggested:";
            int fm = rawAnswer.indexOf(fallback);
            if (fm < 0) return List.of();
            String sf = rawAnswer.substring(fm + fallback.length()).trim();
            return java.util.Arrays.stream(sf.split("[｜|]"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .limit(3)
                    .toList();
        }
        String suffix = rawAnswer.substring(marker + suffixMarker.length()).trim();
        return java.util.Arrays.stream(suffix.split("[｜|]"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .limit(3)
                .toList();
    }

    private List<String> defaultSuggestedQuestions(String userMessage, boolean en) {
        if (en) {
            return List.of(
                    "What are the must-see spots here?",
                    "How long does it take to visit?",
                    "Any good restaurants nearby?"
            );
        }
        // 根据用户消息关键词生成不同的默认追问，避免每次都一样
        String msg = userMessage != null ? userMessage : "";
        if (msg.contains("路线") || msg.contains("怎么走") || msg.contains("导航")) {
            return List.of("这个景点有什么特色？", "附近还有什么好玩的？", "有没有推荐的美食？");
        }
        if (msg.contains("美食") || msg.contains("吃") || msg.contains("餐厅")) {
            return List.of("景点游览需要多久？", "怎么去这个景点？", "附近还有哪些景点？");
        }
        if (msg.contains("历史") || msg.contains("文化") || msg.contains("故事")) {
            return List.of("这个景点怎么去？", "附近有什么好玩的？", "游览全程要多久？");
        }
        if (msg.contains("拍照") || msg.contains("打卡") || msg.contains("网红")) {
            return List.of("什么时间去拍照最好？", "景点有什么特色？", "附近还有哪里值得去？");
        }
        // 默认追问（每次轮流不同顺序）
        int hash = Math.abs(msg.hashCode());
        if (hash % 3 == 0) {
            return List.of("这个景点有什么必看的？", "附近还有哪些景点？", "游览大概需要多长时间？");
        } else if (hash % 3 == 1) {
            return List.of("怎么去这个景点最方便？", "景点有什么特色推荐？", "附近有什么美食？");
        } else {
            return List.of("这个景点有什么历史故事？", "推荐一条游览路线", "附近还有什么值得打卡的？");
        }
    }

    private String buildProfileDesc(Integer age, String gender, String location, boolean en) {
        boolean hasAge = age != null && age > 0;
        boolean hasGender = gender != null && !gender.isBlank();
        boolean hasLocation = location != null && !location.isBlank();
        if (!hasAge && !hasGender && !hasLocation) return "";

        StringBuilder sb = new StringBuilder();
        if (en) {
            sb.append("Visitor profile: ");
            if (hasAge) sb.append(age).append(" years old");
            if (hasGender) {
                if (hasAge) sb.append(", ");
                String g = switch (gender.trim().toLowerCase()) {
                    case "male" -> "male";
                    case "female" -> "female";
                    default -> gender.trim();
                };
                sb.append(g);
            }
            if (hasLocation) {
                if (hasAge || hasGender) sb.append(", ");
                sb.append("from ").append(location.trim());
            }
            sb.append(". Please tailor your response to match this visitor.\n\n");
        } else {
            sb.append("游客信息：");
            if (hasAge) sb.append(age).append("岁");
            if (hasGender) {
                if (hasAge) sb.append("，");
                String g = switch (gender.trim().toLowerCase()) {
                    case "male" -> "男性";
                    case "female" -> "女性";
                    default -> gender.trim();
                };
                sb.append(g);
            }
            if (hasLocation) {
                if (hasAge || hasGender) sb.append("，");
                sb.append("来自").append(location.trim());
            }
            sb.append("。请根据以上信息调整回答的语气和内容。\n\n");
        }
        return sb.toString();
    }

    /** 如果用户提供了 GPS 坐标，查找最近的景点并添加到上下文 */
    private String buildGpsContext(Double latitude, Double longitude, boolean en) {
        if (latitude == null || longitude == null) return "";
        try {
            List<ScenicSpot> spots = scenicSpotMapper.findByStatusOrderBySortOrderAsc((short) 1);
            if (spots.isEmpty()) return "";

            // 找到最近的景点
            ScenicSpot nearest = null;
            double minDist = Double.MAX_VALUE;
            for (ScenicSpot spot : spots) {
                double dist = CheckInService.haversineDistance(
                        latitude, longitude,
                        spot.getLocationLat().doubleValue(),
                        spot.getLocationLng().doubleValue());
                if (dist < minDist) {
                    minDist = dist;
                    nearest = spot;
                }
            }
            if (nearest == null) return "";

            String nearestName = nearest.getName();
            int distM = (int) minDist;
            String dirStr = describeDirection(latitude, longitude,
                    nearest.getLocationLat().doubleValue(), nearest.getLocationLng().doubleValue(), en);

            if (en) {
                return "Your current GPS position shows you are " + distM + "m " + dirStr + " " + nearestName
                        + ". Location info: you are inside " + (distM < 500 ? nearestName : "灵山胜境 scenic area")
                        + ". If the user asks about location or directions, use this info to answer accurately.\n";
            } else {
                return "你的当前位置距离「" + nearestName + "」约" + distM + "米（" + dirStr + "方向）"
                        + "。你在" + (distM < 500 ? nearestName + "附近" : "灵山胜境景区内")
                        + "。如果用户询问位置或路线，请根据以上信息准确回答。\n";
            }
        } catch (Exception e) {
            log.debug("buildGpsContext error: {}", e.getMessage());
            return "";
        }
    }

    /** 描述方向（东南西北） */
    private String describeDirection(double lat1, double lng1, double lat2, double lng2, boolean en) {
        double dLat = lat2 - lat1;
        double dLng = lng2 - lng1;
        String ns = dLat > 0 ? (en ? "north" : "北") : (en ? "south" : "南");
        String ew = dLng > 0 ? (en ? "east" : "东") : (en ? "west" : "西");
        // 判断主方向
        if (Math.abs(dLat) > Math.abs(dLng) * 1.5) return ns + (en ? " of " : "边");
        if (Math.abs(dLng) > Math.abs(dLat) * 1.5) return ew + (en ? " of " : "边");
        return ns + ew + (en ? " of " : "方向");
    }

    private String inferEmotion(String answer) {
        String text = answer == null ? "" : answer.toLowerCase(Locale.ROOT);
        // 激动/兴奋
        if (text.contains("太美") || text.contains("壮丽") || text.contains("一定要") ||
            text.contains("amazing") || text.contains("stunning") || text.contains("breathtaking") ||
            text.contains("wonderful") || text.contains("fantastic") || text.contains("must-see")) {
            return "happy";
        }
        // 热情/推荐
        if (text.contains("推荐") || text.contains("值得") || text.contains("非常适合") || text.contains("欢迎") ||
            text.contains("recommend") || text.contains("worth") || text.contains("highly") || text.contains("great") ||
            text.contains("excellent")) {
            return "happy";
        }
        // 娓娓道来/故事感
        if (text.contains("传说") || text.contains("古老") || text.contains("建于") || text.contains("历史") || text.contains("当年") ||
            text.contains("legend") || text.contains("ancient") || text.contains("built") || text.contains("history") || text.contains("century")) {
            return "calm";
        }
        // 抱歉/否定
        if (text.contains("抱歉") || text.contains("不建议") || text.contains("无法确定") ||
            text.contains("sorry") || text.contains("unfortunately") || text.contains("cannot")) {
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
        public final boolean isEn;
        public String fullResponse;

        SetupResult(String key, Session session, String userMessage, String inputType,
                    String promptUser, LocalDateTime start, List<String> ctx, String isEn) {
            this.key = key;
            this.session = session;
            this.userMessage = userMessage;
            this.inputType = inputType;
            this.promptUser = promptUser;
            this.start = start;
            this.ctx = ctx;
            this.isEn = "en".equals(isEn);
        }
    }

    public record ChatReply(String sessionId, String answer, String ttsUrl, String emotion,
                            List<String> suggestedQuestions, List<String> citations) {
    }
}
