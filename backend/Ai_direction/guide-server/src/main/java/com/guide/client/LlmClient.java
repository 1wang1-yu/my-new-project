package com.guide.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Component
public class LlmClient {

    private static final int MAX_TOKENS = 800;

    private final HttpClient httpClient;
    private final String model;
    private final String apiKey;
    private final String baseUrl;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    public LlmClient(
            @Value("${spring.ai.openai.chat.options.model:qwen-turbo}") String model,
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${spring.ai.openai.base-url}") String baseUrl) {
        this.model = model;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.json = new com.fasterxml.jackson.databind.ObjectMapper();
        log.info("LLM 客户端已初始化: model={}, baseUrl={}", model, baseUrl);
    }

    /** 同步调用（非流式），用于不需要流式的场景（超时 30 秒，适合复杂生成） */
    public String chat(String systemPrompt, String userMessage) {
        StringBuilder full = new StringBuilder();
        chatStream(systemPrompt, userMessage, full::append, 30);
        String result = full.toString();
        if (result.isBlank()) {
            return fallbackAnswer();
        }
        return result;
    }

    /** 流式调用，每收到一个文本片段就回调 consumer（默认 8 秒超时） */
    public void chatStream(String systemPrompt, String userMessage, Consumer<String> onChunk) {
        chatStream(systemPrompt, userMessage, onChunk, 8);
    }

    /** 流式调用，可自定义超时秒数 */
    public void chatStream(String systemPrompt, String userMessage, Consumer<String> onChunk, int timeoutSeconds) {
        try {
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("model", model);
            body.put("max_tokens", MAX_TOKENS);
            body.put("temperature", 0.85);
            body.put("stream", true);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt != null ? systemPrompt : "你是一个专业的旅游助手。"),
                    Map.of("role", "user", "content", userMessage)
            ));

            String payload = json.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<java.io.InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                String errBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                log.error("LLM 流式请求失败: HTTP {} body={}", response.statusCode(), errBody);
                onChunk.accept(fallbackAnswer());
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data)) {
                            break;
                        }
                        try {
                            var root = json.readTree(data);
                            var choices = root.path("choices");
                            if (choices.size() > 0) {
                                var delta = choices.get(0).path("delta");
                                var content = delta.path("content");
                                // 跳过 content=null 或 content="" 的 chunk（如仅设置 role 的首帧）
                                if (!content.isMissingNode() && !content.isNull()) {
                                    String text = content.asText();
                                    if (!text.isBlank()) {
                                        onChunk.accept(text);
                                    }
                                }
                            }
                        } catch (Exception ignored) {
                            // 跳过无法解析的行
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("LLM 流式调用失败: {}", e.getMessage());
            onChunk.accept(fallbackAnswer());
        }
    }

    private String fallbackAnswer() {
        return "我先基于当前景区知识为你给出建议：建议从游客中心开始，优先游览核心景点，再根据体力安排观景平台和休闲区。\n建议追问：这个景点有什么必看的亮点？｜附近还有哪些值得顺路去的景点？｜游览全程大概需要多长时间？";
    }
}
