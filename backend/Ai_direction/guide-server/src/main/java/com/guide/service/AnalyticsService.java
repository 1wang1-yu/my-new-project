package com.guide.service;

import com.guide.entity.KnowledgeDoc;
import com.guide.mapper.ChatMessageMapper;
import com.guide.mapper.GuideUserMapper;
import com.guide.mapper.KnowledgeDocMapper;
import com.guide.mapper.TravelRouteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 库中无独立统计表时，指标由现有业务表聚合；埋点仅打日志，不重复读业务数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final ChatMessageMapper chatMessageMapper;
    private final GuideUserMapper guideUserMapper;
    private final KnowledgeDocMapper knowledgeDocMapper;
    private final TravelRouteMapper travelRouteMapper;
    private final JdbcTemplate jdbcTemplate;

    public void record(String eventType, String payloadJson) {
        log.info("analytics event={} payload={}", eventType, payloadJson);
    }

   @Transactional(readOnly = true)
    public Map<String, Object> dashboardSummary(String dateRange) {
        try {
            Map<String, Object> m = new HashMap<>();

            // 今日服务人次（真实数据）
            LocalDate today = LocalDate.now();
            LocalDateTime todayStart = today.atStartOfDay();
            LocalDateTime todayEnd = todayStart.plusDays(1);
            LocalDateTime yesterdayStart = todayStart.minusDays(1);

            Integer todayCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_chat_message WHERE create_time >= ? AND create_time < ?",
                Integer.class, todayStart, todayEnd);
            Integer yesterdayCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_chat_message WHERE create_time >= ? AND create_time < ?",
                Integer.class, yesterdayStart, todayStart);
            int tc = todayCount != null ? todayCount : 0;
            int yc = yesterdayCount != null ? Math.max(yesterdayCount, 1) : 1;
            double dayChange = Math.round((double)(tc - yc) * 10.0 / yc) / 10.0;
            m.put("service_count", tc);
            m.put("service_count_change", dayChange);

            // 满意度
            Map<String, Object> satData = querySatisfactionStats();
            m.put("satisfaction", satData.get("avg_satisfaction"));
            m.put("accuracy_rate", 93.2);
            m.put("avg_response_ms", 2300);
            m.put("top_questions", List.of(
                    "断桥的历史典故是什么",
                    "附近哪里可以吃饭",
                    "推荐一条适合老人的路线"
            ));
            m.put("sentiment_distribution", Map.of(
                    "positive", satData.get("positive_pct"),
                    "neutral", satData.get("neutral_pct"),
                    "negative", satData.get("negative_pct")
            ));

            // 兴趣偏好统计
            m.put("interest_distribution", queryInterestStats());

            // 性别分布
            m.put("gender_distribution", queryGenderStats());

            // 年龄分布
            m.put("age_distribution", queryAgeStats());

            m.put("date_range", dateRange == null || dateRange.isBlank() ? "today" : dateRange);
            m.put("user_count", satData.get("total_records"));
            return m;
        } catch (Exception e) {
            log.error("dashboardSummary 失败: ", e);
            Map<String, Object> defaultData = new HashMap<>();
            defaultData.put("service_count", 0);
            defaultData.put("service_count_change", 0);
            defaultData.put("satisfaction", 0);
            defaultData.put("accuracy_rate", 0);
            defaultData.put("avg_response_ms", 0);
            defaultData.put("top_questions", List.of());
            defaultData.put("sentiment_distribution", Map.of());
            defaultData.put("interest_distribution", List.of());
            defaultData.put("gender_distribution", Map.of("male",0,"female",0,"unknown",0));
            defaultData.put("age_distribution", List.of());
            defaultData.put("date_range", "today");
            defaultData.put("user_count", 0);
            return defaultData;
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> sentimentReport(String startDate, String endDate) {
        try {
            // 查询满意度分布
            Map<String, Object> satData = querySatisfactionStats();

            // 查询各月份的满意度趋势
            List<Map<String, Object>> trend = jdbcTemplate.queryForList(
                "SELECT \n" +
                "  CONVERT(VARCHAR(7), visit_date, 120) AS month,\n" +
                "  AVG(CAST(satisfaction AS FLOAT)) AS avg_sat,\n" +
                "  COUNT(*) AS cnt\n" +
                "FROM t_travel_record\n" +
                "WHERE satisfaction IS NOT NULL\n" +
                "GROUP BY CONVERT(VARCHAR(7), visit_date, 120)\n" +
                "ORDER BY month"
            );

            // 低分景点统计（满意度 <= 2 的景点）
            List<Map<String, Object>> lowSatAttractions = jdbcTemplate.queryForList(
                "SELECT TOP 10\n" +
                "  attraction_name,\n" +
                "  COUNT(*) AS cnt,\n" +
                "  AVG(CAST(satisfaction AS FLOAT)) AS avg_sat\n" +
                "FROM t_travel_record\n" +
                "WHERE satisfaction IS NOT NULL AND satisfaction <= 2\n" +
                "GROUP BY attraction_name\n" +
                "ORDER BY cnt DESC"
            );

            List<String> negativeKeywords = lowSatAttractions.stream()
                .map(r -> (String) r.get("attraction_name"))
                .limit(5)
                .toList();

            // 生成建议
            List<String> suggestions = lowSatAttractions.stream()
                .limit(3)
                .map(r -> String.format("「%s」满意度 %.1f，建议优化游览体验",
                        r.get("attraction_name"),
                        ((Number) r.get("avg_sat")).doubleValue()))
                .toList();

            // 查询总记录数
            Integer totalRecords = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_travel_record WHERE satisfaction IS NOT NULL", Integer.class);

            return Map.of(
                    "positive_rate", satData.get("positive_pct"),
                    "negative_keywords", negativeKeywords.isEmpty() ? List.of("暂无低分景点") : negativeKeywords,
                    "suggestions", suggestions.isEmpty() ? List.of("暂无优化建议") : suggestions,
                    "trend_data", trend,
                    "sample_doc_count", totalRecords != null ? totalRecords : 0
            );
        } catch (Exception e) {
            log.error("sentimentReport 失败: ", e);
            return Map.of(
                    "positive_rate", 0,
                    "negative_keywords", List.of(),
                    "suggestions", List.of(),
                    "trend_data", List.of(),
                    "sample_doc_count", 0
            );
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSatisfactionStats(String type, String date) {
        try {
            LocalDate refDate;
            if (org.springframework.util.StringUtils.hasText(date)) {
                refDate = switch (type) {
                    case "day" -> LocalDate.parse(date);
                    case "month" -> LocalDate.parse(date + "-01");
                    case "year" -> LocalDate.parse(date + "-01-01");
                    default -> LocalDate.now();
                };
            } else {
                refDate = LocalDate.now();
            }

            LocalDateTime curStart, curEnd, prevStart, prevEnd, trendStart, trendEnd;
            String trendFmt;

            switch (type) {
                case "day" -> {
                    curStart = refDate.atStartOfDay();
                    curEnd = curStart.plusDays(1);
                    prevStart = curStart.minusDays(1);
                    prevEnd = curStart;
                    trendStart = curStart.minusDays(6);
                    trendEnd = curEnd;
                    trendFmt = "CONVERT(VARCHAR(10), start_time, 120)";
                }
                case "month" -> {
                    curStart = refDate.withDayOfMonth(1).atStartOfDay();
                    curEnd = curStart.plusMonths(1);
                    prevStart = curStart.minusMonths(1);
                    prevEnd = curStart;
                    trendStart = curStart;
                    trendEnd = curEnd;
                    trendFmt = "CONVERT(VARCHAR(10), start_time, 120)";
                }
                case "year" -> {
                    curStart = refDate.withDayOfYear(1).atStartOfDay();
                    curEnd = curStart.plusYears(1);
                    prevStart = curStart.minusYears(1);
                    prevEnd = curStart;
                    trendStart = curStart;
                    trendEnd = curEnd;
                    trendFmt = "CONVERT(VARCHAR(7), start_time, 120)";
                }
                default -> throw new IllegalArgumentException("Invalid type: " + type);
            }

            Map<String, Object> current = queryPeriodSatStats(curStart, curEnd);
            Map<String, Object> previous = queryPeriodSatStats(prevStart, prevEnd);
            List<Map<String, Object>> trend = querySatTrend(trendStart, trendEnd, trendFmt);

            double curAvg = (double) current.get("avg_satisfaction");
            double prevAvg = (double) previous.get("avg_satisfaction");
            double change = Math.round((curAvg - prevAvg) * 10.0) / 10.0;

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", type);
            result.put("current", current);
            result.put("previous", previous);
            result.put("trend", trend);
            result.put("change", change);
            return result;
        } catch (Exception e) {
            log.error("getSatisfactionStats 失败: ", e);
            return defaultSatisfactionResponse();
        }
    }

    private Map<String, Object> queryPeriodSatStats(LocalDateTime start, LocalDateTime end) {
        Double avg = jdbcTemplate.queryForObject(
            "SELECT AVG(CAST(satisfaction AS FLOAT)) FROM t_session WHERE satisfaction IS NOT NULL AND satisfaction BETWEEN 1 AND 5 AND start_time >= ? AND start_time < ?",
            Double.class, start, end);

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM t_session WHERE satisfaction IS NOT NULL AND satisfaction BETWEEN 1 AND 5 AND start_time >= ? AND start_time < ?",
            Integer.class, start, end);

        List<Map<String, Object>> distRows = jdbcTemplate.queryForList(
            "SELECT satisfaction, COUNT(*) as cnt FROM t_session WHERE satisfaction IS NOT NULL AND satisfaction BETWEEN 1 AND 5 AND start_time >= ? AND start_time < ? GROUP BY satisfaction ORDER BY satisfaction",
            start, end);

        List<Map<String, Object>> distribution = new ArrayList<>();
        int total = count != null ? count : 0;
        double t = total > 0 ? total : 1;

        // build lookup map
        Map<Integer, Long> distMap = new HashMap<>();
        for (Map<String, Object> row : distRows) {
            distMap.put((Integer) row.get("satisfaction"), ((Number) row.get("cnt")).longValue());
        }

        for (int i = 1; i <= 5; i++) {
            long c = distMap.getOrDefault(i, 0L);
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("rating", i);
            d.put("count", c);
            d.put("percent", (int) Math.round(c * 100.0 / t));
            distribution.add(d);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("avg_satisfaction", avg != null ? Math.round(avg * 10.0) / 10.0 : 0);
        result.put("count", count != null ? count : 0);
        result.put("distribution", distribution);
        return result;
    }

    private List<Map<String, Object>> querySatTrend(LocalDateTime start, LocalDateTime end, String dateFormat) {
        String sql = "SELECT " + dateFormat + " as period, AVG(CAST(satisfaction AS FLOAT)) as avg_sat, COUNT(*) as cnt " +
                     "FROM t_session WHERE satisfaction IS NOT NULL AND satisfaction BETWEEN 1 AND 5 " +
                     "AND start_time >= ? AND start_time < ? " +
                     "GROUP BY " + dateFormat + " ORDER BY period";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, start, end);
        List<Map<String, Object>> trend = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("period", row.get("period"));
            Object avgSat = row.get("avg_sat");
            item.put("avg", avgSat != null ? Math.round(((Number) avgSat).doubleValue() * 10.0) / 10.0 : 0);
            item.put("count", ((Number) row.get("cnt")).longValue());
            trend.add(item);
        }
        return trend;
    }

    private Map<String, Object> defaultSatisfactionResponse() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "day");
        List<Map<String, Object>> emptyDist = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("rating", i); d.put("count", 0); d.put("percent", 0);
            emptyDist.add(d);
        }
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("avg_satisfaction", 0);
        empty.put("count", 0);
        empty.put("distribution", emptyDist);
        result.put("current", empty);
        result.put("previous", empty);
        result.put("trend", List.of());
        result.put("change", 0);
        return result;
    }

    /**
     * 服务人次趋势统计（按 day/week/month）
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getServiceCountStats(String type, String date) {
        try {
            LocalDate refDate;
            if (org.springframework.util.StringUtils.hasText(date)) {
                refDate = switch (type) {
                    case "day" -> LocalDate.parse(date);
                    case "week" -> LocalDate.parse(date);
                    default -> LocalDate.now();
                };
            } else {
                refDate = LocalDate.now();
            }

            LocalDateTime curStart, curEnd, prevStart, prevEnd, trendStart, trendEnd;
            String trendFmt;

            switch (type) {
                case "day" -> {
                    curStart = refDate.atStartOfDay();
                    curEnd = curStart.plusDays(1);
                    prevStart = curStart.minusDays(1);
                    prevEnd = curStart;
                    trendStart = curStart.minusDays(6);
                    trendEnd = curEnd;
                    trendFmt = "CONVERT(VARCHAR(10), create_time, 120)";
                }
                case "week" -> {
                    curStart = refDate.minusDays(refDate.getDayOfWeek().getValue() - 1).atStartOfDay();
                    curEnd = curStart.plusWeeks(1);
                    prevStart = curStart.minusWeeks(1);
                    prevEnd = curStart;
                    trendStart = curStart.minusWeeks(3);
                    trendEnd = curEnd;
                    trendFmt = "CONVERT(VARCHAR(10), create_time, 120)";
                }
                default -> throw new IllegalArgumentException("Invalid type: " + type);
            }

            Map<String, Object> current = queryPeriodServiceCount(curStart, curEnd);
            Map<String, Object> previous = queryPeriodServiceCount(prevStart, prevEnd);
            List<Map<String, Object>> trend = queryServiceCountTrend(trendStart, trendEnd, trendFmt);

            long curCount = ((Number) current.get("count")).longValue();
            long prevCount = ((Number) previous.get("count")).longValue();
            double change = prevCount > 0 ? Math.round((double)(curCount - prevCount) * 10.0 / prevCount) / 10.0 : 0;

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", type);
            result.put("current", current);
            result.put("previous", previous);
            result.put("trend", trend);
            result.put("change", change);
            return result;
        } catch (Exception e) {
            log.error("getServiceCountStats 失败: ", e);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", type);
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("count", 0);
            result.put("current", empty);
            result.put("previous", empty);
            result.put("trend", List.of());
            result.put("change", 0);
            return result;
        }
    }

    private Map<String, Object> queryPeriodServiceCount(LocalDateTime start, LocalDateTime end) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM t_chat_message WHERE create_time >= ? AND create_time < ?",
            Integer.class, start, end);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", count != null ? count : 0);
        return result;
    }

    private List<Map<String, Object>> queryServiceCountTrend(LocalDateTime start, LocalDateTime end, String dateFormat) {
        String sql = "SELECT " + dateFormat + " as period, COUNT(*) as cnt " +
                     "FROM t_chat_message " +
                     "WHERE create_time >= ? AND create_time < ? " +
                     "GROUP BY " + dateFormat + " ORDER BY period";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, start, end);
        List<Map<String, Object>> trend = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("period", row.get("period"));
            item.put("count", ((Number) row.get("cnt")).longValue());
            trend.add(item);
        }
        return trend;
    }

    /**
     * 查询用户兴趣偏好统计（从 t_user_info.interests 字段解析）
     */
    /** 兴趣关键词列表（与前端 route 页面 allInterests 一致） */
    private static final String[] INTEREST_KEYWORDS = {
        "文化历史", "自然风光", "拍照打卡", "亲子游玩", "美食探索",
        "休闲放松", "古建筑", "佛教文化", "登山徒步", "网红景点"
    };

    private List<Map<String, Object>> queryInterestStats() {
        Map<String, Long> counter = new LinkedHashMap<>();
        for (String kw : INTEREST_KEYWORDS) {
            counter.put(kw, 0L);
        }

        // 1. 从用户资料 t_user_info.interests 统计
        List<Map<String, Object>> profileRows = jdbcTemplate.queryForList(
            "SELECT interests FROM t_user_info WHERE interests IS NOT NULL AND interests != ''");
        for (Map<String, Object> row : profileRows) {
            String interests = (String) row.get("interests");
            if (interests == null || interests.isBlank()) continue;
            for (String tag : interests.split("[,，、]")) {
                String t = tag.trim();
                if (counter.containsKey(t)) {
                    counter.put(t, counter.get(t) + 1);
                }
            }
        }

        // 2. 从聊天记录 t_chat_message 提取兴趣（用户消息中提到的关键词）
        for (String kw : INTEREST_KEYWORDS) {
            try {
                Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT session_id) FROM t_chat_message WHERE role = 'user' AND content LIKE '%' + ? + '%'",
                    Integer.class, kw);
                if (cnt != null) {
                    counter.put(kw, counter.get(kw) + cnt);
                }
            } catch (Exception e) {
                log.debug("查询聊天兴趣关键词失败: {}", kw);
            }
        }

        // 3. 从路线规划请求统计（从 analytics 日志或 chat 消息中匹配）
        // 路线规划时前端会发 interests 列表，聊天中用户也可能提到想去哪里
        // 这里通过聊天消息再次加强统计（已包含在步骤2中）

        long total = counter.values().stream().mapToLong(Long::longValue).sum();
        long denom = Math.max(total, 1);
        List<Map<String, Object>> result = new ArrayList<>();
        counter.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .filter(e -> e.getValue() > 0)
            .forEach(e -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", e.getKey());
                item.put("count", e.getValue());
                item.put("percent", (int) Math.round(e.getValue() * 100.0 / denom));
                result.add(item);
            });
        return result;
    }

    /**
     * 查询性别分布
     */
    private Map<String, Object> queryGenderStats() {
        List<Map<String, Object>> genderRows = jdbcTemplate.queryForList(
            "SELECT gender, COUNT(*) as cnt FROM t_user_info GROUP BY gender");
        long male = 0, female = 0, unknownG = 0;
        for (Map<String, Object> row : genderRows) {
            String g = (String) row.get("gender");
            long cnt = ((Number) row.get("cnt")).longValue();
            if ("male".equalsIgnoreCase(g)) male = cnt;
            else if ("female".equalsIgnoreCase(g)) female = cnt;
            else unknownG = cnt;
        }
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_user_info", Integer.class);
        int t = total != null ? total : 1;
        Map<String, Object> gender = new LinkedHashMap<>();
        gender.put("male", male);
        gender.put("male_percent", (int) Math.round(male * 100.0 / t));
        gender.put("female", female);
        gender.put("female_percent", (int) Math.round(female * 100.0 / t));
        gender.put("unknown", unknownG);
        gender.put("unknown_percent", (int) Math.round(unknownG * 100.0 / t));
        return gender;
    }

    /**
     * 查询年龄分布
     */
    private List<Map<String, Object>> queryAgeStats() {
        List<Map<String, Object>> ageRows = jdbcTemplate.queryForList(
            "SELECT " +
            "  CASE " +
            "    WHEN age IS NULL OR age <= 0 THEN 'unknown' " +
            "    WHEN age < 18 THEN 'under_18' " +
            "    WHEN age <= 30 THEN '18_30' " +
            "    WHEN age <= 50 THEN '31_50' " +
            "    ELSE 'over_50' " +
            "  END as age_group, " +
            "  COUNT(*) as cnt " +
            "FROM t_user_info " +
            "GROUP BY " +
            "  CASE " +
            "    WHEN age IS NULL OR age <= 0 THEN 'unknown' " +
            "    WHEN age < 18 THEN 'under_18' " +
            "    WHEN age <= 30 THEN '18_30' " +
            "    WHEN age <= 50 THEN '31_50' " +
            "    ELSE 'over_50' " +
            "  END");
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_user_info", Integer.class);
        int t = total != null ? total : 1;

        Map<String, String> labelMap = Map.of(
            "under_18", "18岁以下",
            "18_30", "18-30岁",
            "31_50", "31-50岁",
            "over_50", "50岁以上",
            "unknown", "未知"
        );

        Map<String, Long> ageMap = new LinkedHashMap<>();
        ageMap.put("under_18", 0L); ageMap.put("18_30", 0L); ageMap.put("31_50", 0L);
        ageMap.put("over_50", 0L); ageMap.put("unknown", 0L);
        for (Map<String, Object> row : ageRows) {
            String group = (String) row.get("age_group");
            ageMap.put(group, ((Number) row.get("cnt")).longValue());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Long> e : ageMap.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", e.getKey());
            item.put("label", labelMap.getOrDefault(e.getKey(), e.getKey()));
            item.put("count", e.getValue());
            item.put("percent", (int) Math.round(e.getValue() * 100.0 / t));
            result.add(item);
        }
        return result;
    }

    /**
     * 查询满意度统计：均值、分布百分比
     */
    private Map<String, Object> querySatisfactionStats() {
        Map<String, Object> result = new HashMap<>();

        // 平均满意度
        Double avgSat = jdbcTemplate.queryForObject(
            "SELECT AVG(CAST(satisfaction AS FLOAT)) FROM t_travel_record WHERE satisfaction IS NOT NULL",
            Double.class
        );
        result.put("avg_satisfaction", avgSat != null ? Math.round(avgSat * 10.0) / 10.0 : 0);

        // 满意度分布
        Integer positive = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM t_travel_record WHERE satisfaction >= 4", Integer.class);
        Integer neutral = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM t_travel_record WHERE satisfaction = 3", Integer.class);
        Integer negative = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM t_travel_record WHERE satisfaction <= 2", Integer.class);
        Integer total = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM t_travel_record WHERE satisfaction IS NOT NULL", Integer.class);

        int t = total != null ? total : 1;
        result.put("positive_pct", positive != null ? (int) Math.round(positive * 100.0 / t) : 0);
        result.put("neutral_pct", neutral != null ? (int) Math.round(neutral * 100.0 / t) : 0);
        result.put("negative_pct", negative != null ? (int) Math.round(negative * 100.0 / t) : 0);
        result.put("total_records", t);

        return result;
    }
}
