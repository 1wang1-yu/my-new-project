package com.guide.mapper;

import com.guide.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminMapper extends JpaRepository<Admin, Long> {

    Optional<Admin> findByUsername(String username);
}
