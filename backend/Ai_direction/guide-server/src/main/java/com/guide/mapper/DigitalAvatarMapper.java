package com.guide.mapper;

import com.guide.entity.DigitalAvatar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DigitalAvatarMapper extends JpaRepository<DigitalAvatar, Long> {

    List<DigitalAvatar> findByStatus(Short status);
}
