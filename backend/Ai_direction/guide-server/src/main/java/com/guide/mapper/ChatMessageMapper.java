package com.guide.mapper;

import com.guide.entity.ChatMessage;
import com.guide.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageMapper extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findTop20BySessionIdOrderByCreateTimeAsc(Long sessionId);

    long countByInputType(String inputType);
}
