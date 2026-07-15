package com.guide.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelColumnAnalysisVO {
    private Integer index;            // 列索引 (0-based)
    private String originalName;      // Excel 原始列头
    private String suggestedName;     // 建议的英文字段名
    private String suggestedType;     // 推断的 SQL 类型：NVARCHAR, INT, BIGINT, FLOAT, DATETIME2, BIT
    private Integer suggestedLength;  // 建议长度（仅 NVARCHAR）
    private List<String> sampleValues; // 前几条样例值
}
