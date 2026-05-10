package com.guide.mapper;

import com.guide.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GuideSessionMapper extends JpaRepository<Session, Long> {

    Optional<Session> findBySessionKey(String sessionKey);
}
