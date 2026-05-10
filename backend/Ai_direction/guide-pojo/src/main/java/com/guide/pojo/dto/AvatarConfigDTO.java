package com.guide.pojo.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class AvatarConfigDTO {
    private String name;
    private String voiceId;
    private BigDecimal voiceSpeed;
    private String styleDesc;
    private Integer isDefault;
}