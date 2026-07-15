package com.guide.config;

import com.guide.mapper.KnowledgeDocMapper;
import com.guide.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 启动时扫描 docs/knowledge 目录下的 .txt 文件，自动导入知识库并索引到 ChromaDB。
 * 已导入的文件会跳过（按 fileUrl 去重），不会重复添加。
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 1)
@RequiredArgsConstructor
public class KnowledgeFileImporter implements CommandLineRunner {

    private final KnowledgeService knowledgeService;
    private final KnowledgeDocMapper knowledgeDocMapper;

    private static final String DOCS_DIR = "docs/knowledge";

    @Override
    public void run(String... args) {
        Path dir = Paths.get(DOCS_DIR);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            log.info("docs/knowledge 目录不存在，跳过文件导入");
            return;
        }

        // 获取已导入的文件名集合（去重用）
        Set<String> importedFiles = knowledgeDocMapper.findAll().stream()
                .map(doc -> doc.getFileUrl())
                .filter(url -> url != null && !url.isBlank())
                .collect(Collectors.toSet());

        try (Stream<Path> files = Files.list(dir)) {
            files.filter(f -> f.toString().endsWith(".txt"))
                    .sorted()
                    .forEach(f -> importFile(f, importedFiles));
        } catch (IOException e) {
            log.error("扫描 docs/knowledge 目录失败: {}", e.getMessage());
        }
    }

    private void importFile(Path filePath, Set<String> importedFiles) {
        String fileName = filePath.getFileName().toString();

        // 跳过已导入的文件
        if (importedFiles.contains(fileName)) {
            log.debug("文件已导入，跳过: {}", fileName);
            return;
        }

        String title = fileName.replaceFirst("^[A-Z]+-\\d+_", "").replace(".txt", "");
        String category = fileName.startsWith("LS") ? "灵山胜境" :
                          fileName.startsWith("NH") ? "拈花湾" : "general";

        try {
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            if (content.isBlank()) {
                log.warn("文件为空，跳过: {}", fileName);
                return;
            }

            knowledgeService.saveDocument(title, category, content, fileName, 0L);
            log.info("已导入知识文档: {} ({})", title, fileName);
        } catch (IOException e) {
            log.error("读取文件失败: {}", fileName, e);
        }
    }
}
