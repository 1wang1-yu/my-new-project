package com.guide.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "t_session")
@Data
@NoArgsConstructor
public class Session {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String sessionKey;
    private Long userId;
    private Long digitalId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer msgCount;
    private Integer satisfaction;
    private Integer status;
}