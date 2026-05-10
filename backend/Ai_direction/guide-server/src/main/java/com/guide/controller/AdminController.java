package com.guide.controller;

import com.guide.annotation.LogOperation;
import com.guide.entity.KnowledgeDoc;
import com.guide.pojo.dto.ApiResponse;
import com.guide.service.AnalyticsService;
import com.guide.service.KnowledgeService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Api(tags = "管理后台")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AnalyticsService analyticsService;
    private final KnowledgeService knowledgeService;
    private final Environment environment;

    @ApiOperation(value = "知识库文档上传", notes = "上传知识库文档，保存标题、分类和文本内容，返回文档 ID 与索引状态。")
    @PostMapping("/knowledge/upload")
    @LogOperation("admin_knowledge_upload")
    public ApiResponse<Map<String, Object>> uploadKnowledge(@RequestParam("file") MultipartFile file,
                                                            @RequestParam("category") String category,
                                                            @RequestParam("title") String title) throws IOException {
        String originalFilename = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : title;
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        KnowledgeDoc doc = knowledgeService.saveDocument(title, category, content, originalFilename, 1L);
        return ApiResponse.ok(Map.of(
                "doc_id", doc.getId(),
                "chunk_count", doc.getChunkCount(),
                "index_status", doc.getIndexStatus()
        ));
    }

    @ApiOperation(value = "数据大屏统计", notes = "按 today/week 维度返回服务量、满意度、准确率、响应时间和热点问题。")
    @GetMapping("/dashboard")
    @LogOperation("admin_dashboard")
    public ApiResponse<Map<String, Object>> dashboard(@RequestParam(value = "date_range", required = false) String dateRange) {
        return ApiResponse.ok(analyticsService.dashboardSummary(dateRange));
    }

    @ApiOperation(value = "游客感受度报告", notes = "根据时间范围返回情感趋势、负向关键词与运营建议。")
    @GetMapping("/report/sentiment")
    @LogOperation("admin_sentiment_report")
    public ApiResponse<Map<String, Object>> sentimentReport(@RequestParam(value = "start_date", required = false) String startDate,
                                                            @RequestParam(value = "end_date", required = false) String endDate) {
        return ApiResponse.ok(analyticsService.sentimentReport(startDate, endDate));
    }

    @ApiOperation(value = "AI 服务配置概览", notes = "返回管理端展示所需的模型、提供商、基础地址和密钥状态。")
    @GetMapping("/system/config")
    public ApiResponse<Map<String, Object>> systemConfig() {
        String baseUrl = environment.getProperty("spring.ai.openai.base-url", "");
        String model = environment.getProperty("spring.ai.openai.chat.options.model", "qwen3.5-flash");
        String apiKey = environment.getProperty("spring.ai.openai.api-key", "");
        return ApiResponse.ok(Map.of(
                "provider", "DashScope Compatible",
                "model", model,
                "base_url", baseUrl,
                "api_key_masked", maskKey(apiKey),
                "api_key_configured", apiKey != null && !apiKey.isBlank()
        ));
    }

    private String maskKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "未配置";
        }
        if (apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
