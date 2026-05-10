package com.guide.mapper;

import com.guide.entity.ScenicSpot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScenicSpotMapper extends JpaRepository<ScenicSpot, Long> {

    List<ScenicSpot> findByStatusOrderBySortOrderAsc(Short status);
}
