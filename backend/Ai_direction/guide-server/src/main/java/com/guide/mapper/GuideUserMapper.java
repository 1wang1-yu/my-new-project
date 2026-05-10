package com.guide.mapper;

import com.guide.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GuideUserMapper extends JpaRepository<User, Long> {

    Optional<User> findByOpenId(String openId);
}
