package com.guide.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Component
public class LlmClient {

    private static final String MODEL = "qwen-turbo";
    private static final int MAX_TOKENS = 400;

    private final HttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    public LlmClient() {
        this.apiKey = "sk-e6fca972c66f43e1ac827b9553bf1842";
        this.baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.json = new com.fasterxml.jackson.databind.ObjectMapper();
    }

    /** 同步调用（非流式），用于不需要流式的场景 */
    public String chat(String systemPrompt, String userMessage) {
        StringBuilder full = new StringBuilder();
        chatStream(systemPrompt, userMessage, full::append);
        String result = full.toString();
        if (result.isBlank()) {
            return fallbackAnswer();
        }
        return result;
    }

    /** 流式调用，每收到一个文本片段就回调 consumer */
    public void chatStream(String systemPrompt, String userMessage, Consumer<String> onChunk) {
        try {
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("model", MODEL);
            body.put("max_tokens", MAX_TOKENS);
            body.put("stream", true);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt != null ? systemPrompt : "你是一个专业的旅游助手。"),
                    Map.of("role", "user", "content", userMessage)
            ));

            String payload = json.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(25))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<java.io.InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                String errBody = new String(response.body().readAllBytes());
                log.error("LLM 流式请求失败: HTTP {} body={}", response.statusCode(), errBody);
                onChunk.accept(fallbackAnswer());
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()))) {
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
                                if (!content.isMissingNode()) {
                                    String text = content.asText();
                                    if (!text.isEmpty()) {
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
        return "我先基于当前景区知识为你给出建议：建议从游客中心开始，优先游览核心景点，再根据体力安排观景平台和休闲区。\n建议追问：附近还有什么值得打卡的地方？｜适合拍照的时间段是什么？｜能帮我规划更轻松的路线吗？";
    }
}
