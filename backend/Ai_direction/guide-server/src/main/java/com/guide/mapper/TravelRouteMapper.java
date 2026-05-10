package com.guide.mapper;


import com.guide.entity.Route;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TravelRouteMapper extends JpaRepository<Route, Long> {

    List<Route> findByStatus(Short status);
}
