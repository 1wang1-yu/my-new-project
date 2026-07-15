package com.guide.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "t_user_info")
@Data
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String username;
    private String password;
    private String openId;
    private String nickName;
    private String avatarUrl;
    private String phone;
    private String interests;
    private Integer age;
    private String gender;
    private Integer visitCount;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
