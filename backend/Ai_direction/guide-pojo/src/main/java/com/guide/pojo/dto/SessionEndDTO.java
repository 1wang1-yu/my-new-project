package com.guide.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SessionEndDTO {
    @NotBlank private String sessionKey;
    private Integer satisfaction;        // 1-5 用户评分
}