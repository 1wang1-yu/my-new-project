package com.guide.config;

import com.guide.entity.KnowledgeDoc;
import com.guide.mapper.KnowledgeDocMapper;
import com.guide.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 启动时批量索引所有未索引的知识文档到 ChromaDB。
 * 处理场景：
 * 1. 已有文档从未写入向量库（indexStatus=0）
 * 2. 上次索引失败的文档（indexStatus=2）重试
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class KnowledgeIndexRunner implements CommandLineRunner {

    private final KnowledgeDocMapper knowledgeDocMapper;
    private final KnowledgeService knowledgeService;

    @Override
    @Transactional
    public void run(String... args) {
        // 仅索引未索引或上次失败的文档，已索引的跳过
        List<KnowledgeDoc> unindexed = knowledgeDocMapper.findUnindexed();
        if (unindexed.isEmpty()) {
            log.info("没有未索引的知识文档（全部已索引）");
            return;
        }
        log.info("发现 {} 篇未索引的知识文档，开始批量索引到 ChromaDB...", unindexed.size());
        int success = 0;
        int fail = 0;
        for (KnowledgeDoc doc : unindexed) {
            try {
                knowledgeService.indexDocument(doc);
                success++;
            } catch (Exception e) {
                log.error("索引失败: id={}, title={}, {}", doc.getId(), doc.getTitle(), e.getMessage());
                fail++;
            }
        }
        log.info("批量索引完成: 成功 {} 篇, 失败 {} 篇", success, fail);
    }
}
