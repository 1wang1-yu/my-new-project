package com.guide.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "t_route_spot")
@Data
@NoArgsConstructor
public class RouteSpot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long routeId;
    private Long spotId;
    private Integer stepOrder;
    private Integer stayMin;
    private String highlight;
}