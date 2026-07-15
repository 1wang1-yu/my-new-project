package com.guide.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guide.client.LlmClient;
import com.guide.entity.ChatMessage;
import com.guide.entity.Session;
import com.guide.mapper.ChatMessageMapper;
import com.guide.mapper.GuideSessionMapper;
import com.guide.mapper.KnowledgeDocMapper;
import com.guide.pojo.dto.RouteRecommendDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteService {

    private final LlmClient llmClient;
    private final KnowledgeService knowledgeService;
    private final ChatMessageMapper chatMessageMapper;
    private final KnowledgeDocMapper knowledgeDocMapper;
    private final GuideSessionMapper guideSessionMapper;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = "你是一个景区路线规划助手。只返回JSON，不要任何解释。";

    /**
     * 根据用户输入生成个性化路线
     */
    public Map<String, Object> generateRoutes(RouteRecommendDTO req) {
        // 1. 合并兴趣偏好
        List<String> interests = req.getInterests() == null || req.getInterests().isEmpty()
                ? List.of("文化", "拍照")
                : req.getInterests();

        int durationMinutes = req.getDurationMinutes() == null ? 120 : req.getDurationMinutes();
        int peopleCount = req.getPeopleCount() == null ? 1 : req.getPeopleCount();
        String startTime = req.getStartTime();
        String endTime = req.getEndTime();

        // 2. 如果有 sessionId，提取聊天记录中的偏好和时间信息
        String chatHistoryPrefs = extractPreferencesFromChat(req.getSessionId());

        // 3. 从聊天记录中提取时间信息，覆盖前端默认时间
        if (req.getSessionId() != null && !req.getSessionId().isBlank()) {
            String[] chatTimes = extractTimeFromChat(req.getSessionId());
            if (chatTimes != null) {
                // 只在用户没有显式设置时间（前端默认值）时覆盖
                boolean useDefaultStart = req.getStartTime() == null || "09:00".equals(req.getStartTime());
                boolean useDefaultEnd = req.getEndTime() == null || "16:00".equals(req.getEndTime());
                if (useDefaultStart && chatTimes[0] != null) {
                    startTime = chatTimes[0];
                    log.info("从聊天记录提取到开始时间: {}", startTime);
                }
                if (useDefaultEnd && chatTimes[1] != null) {
                    endTime = chatTimes[1];
                    log.info("从聊天记录提取到结束时间: {}", endTime);
                }
                // 重新计算时长
                if (startTime != null && endTime != null) {
                    try {
                        String[] s = startTime.split(":");
                        String[] e = endTime.split(":");
                        int mins = (Integer.parseInt(e[0]) * 60 + Integer.parseInt(e[1]))
                                - (Integer.parseInt(s[0]) * 60 + Integer.parseInt(s[1]));
                        if (mins > 0) {
                            durationMinutes = mins;
                        }
                    } catch (Exception ex) {
                        log.debug("计算时长失败: {}", ex.getMessage());
                    }
                }
            }
        }

        // 4. 从知识库检索相关景点信息
        String searchQuery = buildSearchQuery(interests, req.getCustomPreference(), req.getSelectedAttractions());
        List<String> context = knowledgeService.retrieveContext(searchQuery, null, 8);

        // 5. 构建 LLM prompt
        String prompt = buildPrompt(interests, req.getCustomPreference(), req.getSelectedAttractions(),
                chatHistoryPrefs, peopleCount, startTime, endTime, durationMinutes, context);

        // 6. 调用 LLM
        String llmResult;
        try {
            llmResult = llmClient.chat(SYSTEM_PROMPT, prompt);
            log.debug("LLM route response: {}", llmResult);
        } catch (Exception e) {
            log.error("LLM route generation failed: {}", e.getMessage());
            llmResult = "";
        }

        // 7. 解析 LLM 响应
        List<Map<String, Object>> routes = parseLlmResponse(llmResult);

        // 8. 如果解析失败，生成带用户偏好的 fallback 路线
        if (routes == null || routes.isEmpty()) {
            routes = buildFallbackRoutes(interests, req.getCustomPreference(), req.getSelectedAttractions(),
                    peopleCount, startTime, durationMinutes);
        }

        // 9. 返回结果，包含实际使用的起止时间（前端可用来更新选择器）
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("routes", routes);
        result.put("start_time", startTime);
        result.put("end_time", endTime);
        result.put("duration_minutes", durationMinutes);
        return result;
    }

    /** 将 session_key（字符串）转为数据库中的 Long sessionId */
    private Long resolveSessionId(String sessionKey) {
        if (sessionKey == null || sessionKey.isBlank()) return null;
        // 先尝试直接解析为 Long
        try {
            return Long.parseLong(sessionKey);
        } catch (NumberFormatException e) {
            // 再尝试按 session_key 查找
            try {
                java.util.Optional<Session> opt = guideSessionMapper.findBySessionKey(sessionKey);
                if (opt.isPresent()) {
                    return opt.get().getId();
                }
            } catch (Exception ex) {
                log.debug("按 sessionKey 查找失败: {}", ex.getMessage());
            }
        }
        return null;
    }

    /** 从聊天记录中提取用户偏好，特别是用户提到的想去的景点 */
    private String extractPreferencesFromChat(String sessionId) {
        Long sessionLongId = resolveSessionId(sessionId);
        if (sessionLongId == null) return "";
        try {
            List<ChatMessage> messages = chatMessageMapper.findTop20BySessionIdOrderByCreateTimeAsc(sessionLongId);
            if (messages == null || messages.isEmpty()) {
                return "";
            }
            List<String> userMessages = messages.stream()
                    .filter(m -> "user".equals(m.getRole()))
                    .map(ChatMessage::getContent)
                    .filter(c -> c != null && !c.isBlank())
                    .toList();
            if (userMessages.isEmpty()) {
                return "";
            }
            // 获取景区可用的景点列表，帮助 LLM 匹配用户提到的景点
            List<String> knownAttractions = knowledgeDocMapper.findDistinctScenicTitles();
            String attractionList = knownAttractions.isEmpty()
                    ? ""
                    : "景区内可去的景点有：" + String.join("、", knownAttractions) + "\n";

            return "【用户聊天历史】\n"
                    + userMessages.stream().collect(Collectors.joining("\n"))
                    + "\n\n"
                    + attractionList
                    + "请仔细阅读以上聊天记录，特别关注：\n"
                    + "1. 用户提到了哪些想去的具体景点名称（如「我想去灵山大佛」提到的灵山大佛）\n"
                    + "2. 用户表达了哪些兴趣偏好（如喜欢安静、喜欢拍照、带老人小孩等）\n"
                    + "3. 用户提到了哪些时间信息（如「从6点玩到10点」说明游玩时间是6:00-10:00）\n"
                    + "4. 这些信息**必须**在路线规划中体现\n";
        } catch (Exception e) {
            log.debug("读取聊天历史失败: {}", e.getMessage());
            return "";
        }
    }

    /** 从聊天记录中提取游玩时间（开始时间和结束时间） */
    private String[] extractTimeFromChat(String sessionId) {
        Long sessionLongId = resolveSessionId(sessionId);
        if (sessionLongId == null) return null;
        try {
            List<ChatMessage> messages = chatMessageMapper.findTop20BySessionIdOrderByCreateTimeAsc(sessionLongId);
            if (messages == null || messages.isEmpty()) return null;

            String allUserText = messages.stream()
                    .filter(m -> "user".equals(m.getRole()))
                    .map(ChatMessage::getContent)
                    .filter(c -> c != null && !c.isBlank())
                    .collect(Collectors.joining("\n"));
            if (allUserText.isBlank()) return null;

            // 第一步：找到所有时间词及其前导限定词（早晨/上午/下午/晚上等）
            // 模式：可选的限定词 + 数字 + 点/时/:
            java.util.regex.Pattern timePattern = java.util.regex.Pattern.compile(
                    "(凌晨|早晨|早上|清晨|上午|中午|下午|傍晚|晚上|夜晚|今夜)?\\s*(\\d{1,2})\\s*[点时:：]\\s*(\\d{0,2})");
            java.util.regex.Matcher timeMatcher = timePattern.matcher(allUserText);

            // 收集所有找到的时间 (hour, minute, qualifier, position)
            List<TimeInfo> foundTimes = new ArrayList<>();
            while (timeMatcher.find()) {
                String qualifier = timeMatcher.group(1);
                int hour = Integer.parseInt(timeMatcher.group(2));
                int minute = timeMatcher.group(3) != null && !timeMatcher.group(3).isEmpty()
                        ? Integer.parseInt(timeMatcher.group(3)) : 0;
                // 12 → 24 小时转换
                hour = convertTo24Hour(hour, qualifier);
                foundTimes.add(new TimeInfo(hour, minute, qualifier, timeMatcher.start()));
            }

            if (foundTimes.size() < 2) return null;

            // 第二步：找"从X到Y"或"X到Y"或"X-Y"中的起止时间
            // 取第一个时间作为开始，最后一个时间作为结束
            TimeInfo start = foundTimes.get(0);
            TimeInfo end = foundTimes.get(foundTimes.size() - 1);

            // 验证：如果结束时间小于开始时间，且没有明确的下午/晚上限定，尝试加12小时
            int endHour = end.hour;
            if (endHour < start.hour) {
                endHour += 12;
            }
            // 如果加了12后仍然小于或等于开始时间，再加12（到次日）
            if (endHour <= start.hour) {
                endHour += 12;
            }
            // 不超过24点
            if (endHour >= 24) endHour = 23;

            String startStr = String.format("%02d:%02d", start.hour, start.minute);
            String endStr = String.format("%02d:%02d", Math.min(endHour, 23), end.minute);

            log.info("从聊天提取到时间: {} → {} (原始: {}h → {}h, 限定词: {} → {})",
                    startStr, endStr, start.hour, endHour, start.qualifier, end.qualifier);

            return new String[]{startStr, endStr};
        } catch (Exception e) {
            log.debug("提取时间失败: {}", e.getMessage());
        }
        return null;
    }

    /** 12 → 24 小时转换 */
    private int convertTo24Hour(int hour, String qualifier) {
        if (qualifier == null) return hour;
        switch (qualifier) {
            case "凌晨":
            case "清晨":
                // 凌晨1点=1, 凌晨5点=5（已经是24小时制）
                return hour;
            case "早晨":
            case "早上":
            case "上午":
                // 上午8点=8（不变）
                return hour;
            case "中午":
                // 中午12点左右不变，但说"中午1点"不常见
                return hour < 11 ? hour + 12 : hour;
            case "下午":
                // 下午1点=13, 下午5点=17
                return hour < 12 ? hour + 12 : hour;
            case "傍晚":
            case "晚上":
            case "夜晚":
            case "今夜":
                // 晚上8点=20, 晚上10点=22
                return hour < 12 ? hour + 12 : hour;
            default:
                return hour;
        }
    }

    /** 时间信息内部类 */
    private static class TimeInfo {
        final int hour;
        final int minute;
        final String qualifier;
        final int position;
        TimeInfo(int hour, int minute, String qualifier, int position) {
            this.hour = hour;
            this.minute = minute;
            this.qualifier = qualifier;
            this.position = position;
        }
    }

    /** 构建知识库搜索关键词 */
    private String buildSearchQuery(List<String> interests, String customPreference, List<String> selectedAttractions) {
        List<String> parts = new ArrayList<>(interests);
        if (customPreference != null && !customPreference.isBlank()) {
            parts.add(customPreference);
        }
        if (selectedAttractions != null && !selectedAttractions.isEmpty()) {
            parts.addAll(selectedAttractions);
        }
        return String.join(" ", parts);
    }

    /** 构建 LLM Prompt */
    private String buildPrompt(List<String> interests, String customPreference, List<String> selectedAttractions,
                               String chatHistoryPrefs, int peopleCount, String startTime, String endTime,
                               int durationMinutes, List<String> context) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个景区路线规划专家。请根据以下游客信息和景区知识，规划2条不同的游览路线。\n\n");

        // 游客信息
        sb.append("【游客信息】\n");
        sb.append("- 兴趣偏好：").append(String.join("、", interests)).append("\n");
        if (customPreference != null && !customPreference.isBlank()) {
            sb.append("- 用户自定义要求：").append(customPreference).append("\n");
        }
        if (selectedAttractions != null && !selectedAttractions.isEmpty()) {
            sb.append("- 指定想去的景点：").append(String.join("、", selectedAttractions)).append("\n");
        }
        sb.append("- 人数：").append(peopleCount).append("人\n");
        if (startTime != null) sb.append("- 开始时间：").append(startTime).append("\n");
        if (endTime != null) sb.append("- 结束时间：").append(endTime).append("\n");
        sb.append("- 游玩时长：").append(durationMinutes).append("分钟\n\n");

        // 聊天历史偏好
        if (!chatHistoryPrefs.isBlank()) {
            sb.append(chatHistoryPrefs).append("\n");
        }

        // 景区知识
        sb.append("【景区知识参考】\n");
        sb.append(context.isEmpty() ? "暂无知识库相关数据" : String.join("\n", context));
        sb.append("\n\n");

        // 聊天记录中提取的景点
        if (!chatHistoryPrefs.isBlank()) {
            sb.append("注意：以上「【用户聊天历史】」中用户可能提到了想去的景点或兴趣偏好。\n");
            sb.append("请从中提取用户提到的景点名称，将其视为「用户想去的景点」并纳入路线。\n");
            sb.append("例如：用户说「我想去灵山大佛看看」，则路线必须包含灵山大佛。\n");
            sb.append("例如：用户说「推荐几个适合拍照的地方」，则路线应侧重推荐拍照点。\n\n");
        }

        // 约束条件（严格）
        sb.append("【必须严格遵守的约束】\n");
        sb.append("1. **严格遵循用户兴趣偏好**：如果用户选择了「文化历史」兴趣，路线必须包含历史文化类景点；选择了「自然风光」则重点推荐自然景观类景点；选择了「亲子游玩」则推荐适合家庭和儿童的项目。\n");
        sb.append("2. **严格遵循用户自定义要求**：例如用户说「喜欢安静」，则避开人流密集区，推荐清晨时段游览或小众景点；用户说「喜欢拍照」，则推荐最佳拍照点；用户说「带老人」，则推荐平坦无障碍、有休息区的路线。\n");
        if (selectedAttractions != null && !selectedAttractions.isEmpty()) {
            sb.append("3. **必须包含用户指定的景点**：路线规划中**必须**包含用户在「想去景点」中选择的所有景点，将其合理安排在路线中。\n");
        } else {
            sb.append("3. **注意聊天历史中提到的景点**：如果用户聊天中提到了想去的景点（如「我想去灵山大佛」），则等同于「用户指定景点」，路线中**必须**包含这些景点。\n");
        }
        sb.append("4. 每条路线3-6个景点，每个景点必须包含time(时间段)、name(景点名)、desc(简短描述)\n");
        sb.append("5. 景点名和时间要合理，时间段从游客的开始时间到结束时间覆盖\n");
        sb.append("6. estimated_time单位为分钟，等于所有景点时间总和\n");
        sb.append("7. 返回2条路线，每条路线不同风格\n");
        sb.append("8. 景点名必须基于景区知识参考中的实际景点，不要编造\n");
        sb.append("9. 路线的亮点描述要体现满足了用户的哪些具体偏好\n\n");

        // JSON 格式要求
        sb.append("【返回格式】\n");
        sb.append("请严格按以下JSON格式返回（不要带markdown标记，纯JSON）：\n");
        sb.append("{\"routes\":[{\"name\":\"路线名称\",\"estimated_time\":240,\"stops\":[{\"time\":\"09:00-10:00\",\"name\":\"景点名\",\"desc\":\"简短描述\"}],\"highlights\":[\"亮点1\",\"亮点2\"]}]}");

        return sb.toString();
    }

    /** 解析 LLM 返回的 JSON */
    private List<Map<String, Object>> parseLlmResponse(String llmResult) {
        if (llmResult == null || llmResult.isBlank()) {
            return null;
        }
        try {
            // 尝试清理 markdown 标记
            String cleaned = llmResult.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("```[a-zA-Z]*\n?", "").trim();
                if (cleaned.endsWith("```")) {
                    cleaned = cleaned.substring(0, cleaned.lastIndexOf("```")).trim();
                }
            }

            var root = objectMapper.readTree(cleaned);
            if (root.has("routes")) {
                return objectMapper.convertValue(root.get("routes"),
                        new TypeReference<List<Map<String, Object>>>() {});
            }
            // 如果根节点就是数组
            if (root.isArray()) {
                return objectMapper.convertValue(root,
                        new TypeReference<List<Map<String, Object>>>() {});
            }
        } catch (Exception e) {
            log.warn("解析 LLM 路线结果失败: {}", e.getMessage());
        }
        return null;
    }

    /** 构建 Fallback 路线（当 LLM 调用失败时使用） */
    private List<Map<String, Object>> buildFallbackRoutes(List<String> interests, String customPreference,
                                                           List<String> selectedAttractions, int peopleCount,
                                                           String startTime, int durationMinutes) {
        String start = (startTime != null && startTime.contains(":")) ? startTime : "09:00";
        List<Map<String, Object>> stops1 = new ArrayList<>();
        List<Map<String, Object>> stops2 = new ArrayList<>();

        // 根据兴趣生成不同的默认景点
        boolean preferNature = interests.contains("自然风光") || interests.contains("休闲放松");
        boolean preferCulture = interests.contains("文化历史") || interests.contains("古建筑") || interests.contains("佛教文化");
        boolean preferFamily = interests.contains("亲子游玩");
        boolean preferPhoto = interests.contains("拍照打卡") || interests.contains("网红景点");

        // 如果有指定景点，优先使用
        if (selectedAttractions != null && !selectedAttractions.isEmpty()) {
            int slotMinutes = Math.max(60, durationMinutes / selectedAttractions.size());
            for (int i = 0; i < selectedAttractions.size(); i++) {
                String t = addHour(start, i * slotMinutes / 60);
                String tEnd = addHour(start, (i + 1) * slotMinutes / 60);
                stops1.add(Map.of(
                        "time", t + "-" + tEnd,
                        "name", selectedAttractions.get(i),
                        "desc", "指定景点"
                ));
                stops2.add(Map.of(
                        "time", t + "-" + tEnd,
                        "name", selectedAttractions.get(i),
                        "desc", "指定景点"
                ));
            }
        } else {
            // 根据兴趣偏好生成路线
            if (preferCulture) {
                stops1.add(stop(start, 0, 1, "灵山大佛", "88米青铜大佛，面朝太湖，庄严宏伟"));
                stops1.add(stop(start, 1, 2, "灵山梵宫", "佛教艺术殿堂，穹顶天象图与琉璃壁画"));
                stops1.add(stop(start, 2, 3, "祥符禅寺", "千年古刹，玄奘法师因缘之地"));
                stops1.add(stop(start, 3, 4, "五印坛城", "藏传佛教建筑群，转经筒祈福"));
            } else if (preferNature) {
                stops1.add(stop(start, 0, 1, "菩提大道", "林荫大道，欣赏太湖与山景"));
                stops1.add(stop(start, 1, 2, "九龙灌浴", "音乐喷泉表演，接祈福圣水"));
                stops1.add(stop(start, 2, 3, "灵山精舍", "禅意园林，品素斋赏景"));
                stops1.add(stop(start, 3, 4, "曼飞龙塔", "傣族风格佛塔，静谧园林"));
            } else if (preferFamily) {
                stops1.add(stop(start, 0, 1, "九龙灌浴", "太子佛喷泉表演，孩子喜欢"));
                stops1.add(stop(start, 1, 2, "百子戏弥勒", "青铜雕塑，童趣十足"));
                stops1.add(stop(start, 2, 3, "佛手广场", "摸「天下第一掌」沾福气"));
                stops1.add(stop(start, 3, 4, "灵山梵宫", "色彩斑斓的艺术殿堂"));
            } else if (preferPhoto) {
                stops1.add(stop(start, 0, 1, "灵山大佛", "正面广场拍摄全貌"));
                stops1.add(stop(start, 1, 2, "灵山梵宫", "星空穹顶与飞天壁画"));
                stops1.add(stop(start, 2, 3, "香水海", "湖面倒影拍摄"));
                stops1.add(stop(start, 3, 4, "五印坛城", "藏式建筑金顶红墙"));
            } else {
                stops1.add(stop(start, 0, 1, "灵山大佛", "88米青铜大佛"));
                stops1.add(stop(start, 1, 2, "灵山梵宫", "佛教艺术殿堂"));
                stops1.add(stop(start, 2, 3, "九龙灌浴", "音乐喷泉表演"));
                stops1.add(stop(start, 3, 4, "五印坛城", "藏传佛教建筑"));
            }

            // 第二条路线（与第一条不同风格）
            if (preferCulture) {
                stops2.add(stop(start, 0, 1, "九龙灌浴", "观赏太子佛喷泉表演"));
                stops2.add(stop(start, 1, 2, "灵山梵宫", "东阳木雕与《吉祥颂》演出"));
                stops2.add(stop(start, 2, 3, "佛手广场", "摸掌祈福"));
                stops2.add(stop(start, 3, 4, "祥符禅寺", "千年银杏与古井"));
            } else {
                stops2.add(stop(start, 0, 1, "景区入口", "正门入园"));
                stops2.add(stop(start, 1, 2, "佛足坛", "礼佛起步"));
                stops2.add(stop(start, 2, 3, "杏坛广场", "休憩观景"));
                stops2.add(stop(start, 3, 4, "文创商店", "选购纪念品"));
            }
        }

        // 构建亮点
        List<String> highlights1 = new ArrayList<>();
        highlights1.add("贴合兴趣：" + String.join("、", interests));
        if (peopleCount > 1) highlights1.add("适合" + peopleCount + "人同行");
        if (customPreference != null && !customPreference.isBlank()) {
            highlights1.add("已考虑：" + customPreference);
        }
        if (selectedAttractions != null && !selectedAttractions.isEmpty()) {
            highlights1.add("包含所有指定景点");
        }

        List<Map<String, Object>> routes = new ArrayList<>();
        routes.add(Map.of(
                "name", preferCulture ? "文化深度游" :
                        preferNature ? "自然休闲游" :
                        preferFamily ? "亲子欢乐游" :
                        preferPhoto ? "摄影打卡游" : "经典全景游",
                "stops", stops1,
                "estimated_time", durationMinutes,
                "highlights", highlights1
        ));

        List<String> highlights2 = List.of(
                "不同风格体验",
                peopleCount > 1 ? "适合" + peopleCount + "人同行" : "适合独自漫步",
                "覆盖次要景点"
        );
        routes.add(Map.of(
                "name", preferCulture ? "轻松文化游" :
                        preferNature ? "静心禅意游" :
                        preferFamily ? "趣味探索游" :
                        preferPhoto ? "休闲随拍游" : "休闲打卡之旅",
                "stops", stops2,
                "estimated_time", Math.max(60, durationMinutes - 30),
                "highlights", highlights2
        ));

        return routes;
    }

    /** 快捷创建 stop */
    private Map<String, Object> stop(String baseTime, int startHour, int endHour, String name, String desc) {
        return Map.of(
                "time", addHour(baseTime, startHour) + "-" + addHour(baseTime, endHour),
                "name", name,
                "desc", desc
        );
    }

    /** 时间字符串加N小时 */
    private String addHour(String time, int hours) {
        if (time == null || !time.contains(":")) return "10:00";
        try {
            int h = Integer.parseInt(time.split(":")[0]) + hours;
            return String.format("%02d", h % 24) + ":" + time.split(":")[1];
        } catch (Exception e) {
            return "10:00";
        }
    }
}
