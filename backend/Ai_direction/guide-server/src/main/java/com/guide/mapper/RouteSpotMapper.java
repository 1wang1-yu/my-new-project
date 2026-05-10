package com.guide.mapper;

import com.guide.entity.RouteSpot;
import com.guide.entity.Route;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RouteSpotMapper extends JpaRepository<RouteSpot, Long> {

    List<RouteSpot> findByRouteIdOrderByStepOrderAsc(Long routeId);
}
