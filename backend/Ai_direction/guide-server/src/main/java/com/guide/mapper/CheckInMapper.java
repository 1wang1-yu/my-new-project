package com.guide.mapper;

import com.guide.entity.CheckIn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CheckInMapper extends JpaRepository<CheckIn, Long> {

    List<CheckIn> findByUserIdOrderByCheckInTimeDesc(Long userId);

    Optional<CheckIn> findByUserIdAndSpotId(Long userId, Long spotId);

    void deleteAllByUserId(Long userId);
}
