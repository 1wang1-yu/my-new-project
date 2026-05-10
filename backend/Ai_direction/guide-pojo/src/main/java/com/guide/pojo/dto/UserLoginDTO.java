package com.guide.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserLoginDTO {
    @NotBlank private String code;       // 微信登录 code
    private String nickName;
    private String avatarUrl;
}