package com.guide.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KnowledgeUploadDTO {
    @NotBlank  private String title;
    @NotBlank  private String category;
    private Long scenicSpotId;
    // 文件通过 MultipartFile 单独传，不放这里
}