package com.guide.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@Entity
@Table(name = "t_user")
@Data
@NoArgsConstructor
public class User {
    @Id
    private Long id1;
    private Long id;
    private String openId;
    private String nickName;
    private String avatarUrl;
    private String phone;
    private String interests;
    private Integer visitCount;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
