package com.guide.client;

import com.guide.config.GuideProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * ChromaDB REST API 客户端（默认 v1 查询语义检索）。
 */
@Component
@RequiredArgsConstructor
public class VectorDbClient {

    private final GuideProperties guideProperties;
    private final RestClient.Builder restClientBuilder;

    @SuppressWarnings("unchecked")
    public List<String> query(String collectionName, List<List<Float>> queryEmbeddings, int nResults) {
        String base = guideProperties.getChroma().getBaseUrl().replaceAll("/$", "");
        String name = collectionName != null ? collectionName : guideProperties.getChroma().getDefaultCollection();
        String url = base + "/api/v1/collections/" + name + "/query";

        Map<String, Object> payload = Map.of(
                "query_embeddings", queryEmbeddings,
                "n_results", nResults
        );

        try {
            Map<String, Object> res = restClientBuilder.build()
                    .post()
                    .uri(url)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
            if (res == null || res.get("documents") == null) {
                return List.of();
            }
            Object docs = res.get("documents");
            if (docs instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof List<?> inner) {
                return inner.stream().map(Object::toString).toList();
            }
            return List.of();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return List.of();
            }
            throw e;
        }
    }
}
