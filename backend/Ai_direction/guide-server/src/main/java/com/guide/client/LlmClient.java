package com.guide.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 封装大模型调用（直接HTTP调用，避免Spring AI版本问题）。
 */
@Slf4j
@Component
public class LlmClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String baseUrl;

    public LlmClient() {
        this.apiKey = "sk-e6fca972c66f43e1ac827b9553bf1842";
        this.baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public String chat(String systemPrompt, String userMessage) {
        try {
            log.info("开始调用大模型API...");
            log.info("System Prompt: {}", systemPrompt);
            log.info("User Message: {}", userMessage);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "qwen3.5");
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt != null ? systemPrompt : "你是一个专业的旅游助手。"),
                    Map.of("role", "user", "content", userMessage)
            ));

            Map<String, Object> response = restClient.post()
                    .uri("/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            log.info("API响应: {}", response);

            if (response != null && response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    Map<String, Object> message = (Map<String, Object>) choice.get("message");
                    if (message != null && message.containsKey("content")) {
                        String content = message.get("content").toString();
                        log.info("成功获取AI回答: {}", content);
                        return content;
                    }
                }
            }
            log.warn("API响应格式不符合预期");
            return "抱歉，我暂时没能生成回答，你可以换个问法再试试。";
        } catch (Exception e) {
            log.error("LLM 调用失败: ", e);
            return "我先基于当前景区知识为你给出建议：建议从游客中心开始，优先游览核心景点，再根据体力安排观景平台和休闲区。\n建议追问：附近还有什么值得打卡的地方？｜适合拍照的时间段是什么？｜能帮我规划更轻松的路线吗？";
        }
    }
}