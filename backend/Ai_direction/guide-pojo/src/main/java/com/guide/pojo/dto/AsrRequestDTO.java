package com.guide.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AsrRequestDTO {
    @JsonProperty("audio_base64")
    private String audioBase64;

    private String format;
}
