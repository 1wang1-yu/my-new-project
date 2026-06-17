package com.guide.service;

import com.guide.client.VectorDbClient;
import com.guide.mapper.KnowledgeDocMapper;
import com.guide.entity.KnowledgeDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KnowledgeDocMapper knowledgeDocMapper;
    private final VectorDbClient vectorDbClient;

    @Transactional(readOnly = true)
    public List<String> retrieveContext(String userQuery, List<List<Float>> queryEmbedding, int topK) {
        // 1) 向量语义检索优先
        if (queryEmbedding != null && !queryEmbedding.isEmpty()) {
            List<String> fromVector = vectorDbClient.query(null, queryEmbedding, topK);
            if (!fromVector.isEmpty()) {
                return fromVector;
            }
        }
        // 2) 整句 LIKE 匹配
        List<KnowledgeDoc> docs = knowledgeDocMapper.searchByKeyword(userQuery);
        // 3) 拆词逐词搜索
        if (docs.isEmpty()) {
            docs = searchByWords(userQuery);
        }
        // 4) 兜底：返回最新文档
        if (docs.isEmpty()) {
            docs = knowledgeDocMapper.findTopN(PageRequest.of(0, topK));
            log.info("关键词未命中，返回最新 {} 篇文档作为上下文", docs.size());
        }
        return docs.stream()
                .map(KnowledgeDoc::getContent)
                .filter(c -> c != null && !c.isBlank())
                .limit(topK)
                .toList();
    }

    private List<KnowledgeDoc> searchByWords(String query) {
        LinkedHashSet<KnowledgeDoc> result = new LinkedHashSet<>();
        for (String word : splitWords(query)) {
            result.addAll(knowledgeDocMapper.searchByKeyword(word));
        }
        return new ArrayList<>(result);
    }

    /**
     * 按2-4字切词，用于多关键词模糊匹配
     */
    private List<String> splitWords(String query) {
        List<String> words = new ArrayList<>();
        if (query == null || query.isBlank()) return words;
        String clean = query.replaceAll("[，。！？、\\s]+", "");
        for (int len = 4; len >= 2; len--) {
            for (int i = 0; i <= clean.length() - len; i++) {
                words.add(clean.substring(i, i + len));
            }
        }
        return words.stream().distinct().limit(10).toList();
    }

    @Transactional
    public KnowledgeDoc saveDocument(String title, String category, String content, String fileUrl, Long operatorId) {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setTitle(title);
        doc.setCategory(category != null ? category : "general");
        doc.setContent(content);
        doc.setFileUrl(fileUrl);
        doc.setChunkCount(0);
        doc.setIndexStatus(0);
        doc.setOperatorId(operatorId);
        LocalDateTime now = LocalDateTime.now();
        doc.setCreateTime(now);
        doc.setUpdateTime(now);
        return knowledgeDocMapper.save(doc);
    }

    @Transactional(readOnly = true)
    public long countDocuments() {
        return knowledgeDocMapper.count();
    }
}
