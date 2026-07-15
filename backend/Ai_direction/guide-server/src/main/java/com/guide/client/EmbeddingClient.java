package com.guide.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 调用 DashScope Embedding API 将文本转为向量。
 */
@Slf4j
@Component
public class EmbeddingClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String baseUrl;

    public EmbeddingClient() {
        this.apiKey = "sk-ws-H.EMYXXED.Hqom.MEQCIBOsN2arr55ch1z5PWMRxkhfuWZG3T2wbvX4zqRybsBnAiBI_2jMX6tiXHzR0-38MjOmwCfjmten6U9BSYbMlQMb0A";
        this.baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @SuppressWarnings("unchecked")
    public List<Float> embed(String text) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", "text-embedding-v1");
            body.put("input", text);

            Map<String, Object> res = restClient.post()
                    .uri("/embeddings")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (res != null && res.containsKey("data")) {
                List<Map<String, Object>> data = (List<Map<String, Object>>) res.get("data");
                if (!data.isEmpty()) {
                    Object embedding = data.get(0).get("embedding");
                    if (embedding instanceof List<?>) {
                        return ((List<?>) embedding).stream()
                                .map(v -> ((Number) v).floatValue())
                                .toList();
                    }
                }
            }
            log.warn("Embedding API 返回格式异常: {}", res);
            return List.of();
        } catch (Exception e) {
            log.error("Embedding 调用失败: {}", e.getMessage());
            return List.of();
        }
    }
}
