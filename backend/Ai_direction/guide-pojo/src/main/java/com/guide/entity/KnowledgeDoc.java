package com.guide.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "t_knowledge_doc")
@Data
@NoArgsConstructor
public class KnowledgeDoc {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(columnDefinition = "NVARCHAR(255)")
    private String title;
    @Column(columnDefinition = "NVARCHAR(100)")
    private String category;
    @Lob @Column(columnDefinition = "NVARCHAR(MAX)")
    private String content;
    private String fileUrl;
    private Integer chunkCount;
    private Integer indexStatus;    // 0待索引 1已索引 2失败
    private Long scenicSpotId;
    private Long operatorId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}