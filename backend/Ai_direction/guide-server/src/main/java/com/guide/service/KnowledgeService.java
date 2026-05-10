package com.guide.service;

import com.guide.client.VectorDbClient;
import com.guide.mapper.KnowledgeDocMapper;
import com.guide.entity.KnowledgeDoc;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KnowledgeDocMapper knowledgeDocMapper;
    private final VectorDbClient vectorDbClient;

    @Transactional(readOnly = true)
    public List<String> retrieveContext(String userQuery, List<List<Float>> queryEmbedding, int topK) {
        if (queryEmbedding != null && !queryEmbedding.isEmpty()) {
            List<String> fromVector = vectorDbClient.query(null, queryEmbedding, topK);
            if (!fromVector.isEmpty()) {
                return fromVector;
            }
        }
        return knowledgeDocMapper.searchByKeyword(userQuery).stream()
                .map(KnowledgeDoc::getContent)
                .filter(c -> c != null && !c.isBlank())
                .limit(topK)
                .toList();
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
