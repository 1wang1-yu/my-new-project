package com.guide.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "t_chat_message")
@Data
@NoArgsConstructor
public class ChatMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long sessionId;
    private Long userId;
    private String role;            // user / assistant
    private String inputType;       // text / voice
    @Lob private String content;
    private String audioUrl;
    private String ttsUrl;
    private String emotion;         // positive / neutral / negative
    private Integer responseMs;
    private Integer tokensUsed;
    private LocalDateTime createTime;
}