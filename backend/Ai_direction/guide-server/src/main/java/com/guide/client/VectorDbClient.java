package com.guide.client;

import com.guide.config.GuideProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ChromaDB REST API v2 客户端。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorDbClient {

    private static final String TENANT = "default_tenant";
    private static final String DATABASE = "default_database";

    private final GuideProperties guideProperties;
    private final RestClient.Builder restClientBuilder;
    private final ConcurrentHashMap<String, String> collectionIdCache = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public List<String> query(String collectionName, List<List<Float>> queryEmbeddings, int nResults) {
        String base = guideProperties.getChroma().getBaseUrl().replaceAll("/$", "");
        String name = collectionName != null ? collectionName : guideProperties.getChroma().getDefaultCollection();
        String collId = getCollectionId(base, name);
        if (collId == null) {
            return List.of();
        }

        String url = base + "/api/v2/tenants/" + TENANT + "/databases/" + DATABASE
                + "/collections/" + collId + "/query";

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
                collectionIdCache.remove(name);
                return List.of();
            }
            log.warn("向量库查询失败 HTTP {}: {}", e.getStatusCode().value(), e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.warn("向量库查询失败，回退到关键词检索: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 向 ChromaDB 添加文档（含向量）。
     */
    public void addDocuments(String collectionName, List<String> ids, List<List<Float>> embeddings,
                              List<String> documents, List<Map<String, String>> metadatas) {
        String base = guideProperties.getChroma().getBaseUrl().replaceAll("/$", "");
        String name = collectionName != null ? collectionName : guideProperties.getChroma().getDefaultCollection();
        String collId = getOrCreateCollection(base, name);
        if (collId == null) {
            throw new RuntimeException("无法获取或创建 Chroma collection: " + name);
        }

        String url = base + "/api/v2/tenants/" + TENANT + "/databases/" + DATABASE
                + "/collections/" + collId + "/add";

        Map<String, Object> body = new HashMap<>();
        body.put("ids", ids);
        body.put("embeddings", embeddings);
        body.put("documents", documents);
        if (metadatas != null) {
            body.put("metadatas", metadatas);
        }

        try {
            restClientBuilder.build()
                    .post()
                    .uri(url)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("ChromaDB 写入成功: {} 条文档到 collection={}", ids.size(), name);
        } catch (Exception e) {
            log.error("ChromaDB 写入失败: {}", e.getMessage());
            throw new RuntimeException("ChromaDB 写入失败: " + e.getMessage(), e);
        }
    }

    /** 获取或创建 collection，不存在则自动创建 */
    @SuppressWarnings("unchecked")
    private String getOrCreateCollection(String base, String collectionName) {
        String cached = collectionIdCache.get(collectionName);
        if (cached != null) return cached;

        String listUrl = base + "/api/v2/tenants/" + TENANT + "/databases/" + DATABASE + "/collections";
        try {
            List<Map<String, Object>> collections = restClientBuilder.build()
                    .get()
                    .uri(listUrl)
                    .retrieve()
                    .body(List.class);
            if (collections != null) {
                for (Map<String, Object> c : collections) {
                    if (collectionName.equals(c.get("name"))) {
                        String id = (String) c.get("id");
                        collectionIdCache.put(collectionName, id);
                        return id;
                    }
                }
            }

            // 不存在则创建
            Map<String, Object> createBody = Map.of(
                    "name", collectionName,
                    "metadata", Map.of("hnsw:space", "cosine")
            );
            Map<String, Object> created = restClientBuilder.build()
                    .post()
                    .uri(listUrl)
                    .body(createBody)
                    .retrieve()
                    .body(Map.class);
            if (created != null && created.get("id") != null) {
                String id = (String) created.get("id");
                collectionIdCache.put(collectionName, id);
                log.info("ChromaDB 已创建新 collection: name={}, id={}", collectionName, id);
                return id;
            }
        } catch (Exception e) {
            log.error("获取/创建 Chroma collection 失败: {}", e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String getCollectionId(String base, String collectionName) {
        return collectionIdCache.computeIfAbsent(collectionName, name -> {
            String url = base + "/api/v2/tenants/" + TENANT + "/databases/" + DATABASE + "/collections";
            try {
                List<Map<String, Object>> collections = restClientBuilder.build()
                        .get()
                        .uri(url)
                        .retrieve()
                        .body(List.class);
                if (collections != null) {
                    for (Map<String, Object> c : collections) {
                        if (name.equals(c.get("name"))) {
                            return (String) c.get("id");
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("获取 Chroma collection 列表失败: {}", e.getMessage());
            }
            return null;
        });
    }

    public void clearCache() {
        collectionIdCache.clear();
    }
}
