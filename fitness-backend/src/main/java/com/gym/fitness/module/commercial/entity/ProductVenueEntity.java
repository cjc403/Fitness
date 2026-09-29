package com.gym.fitness.module.commercial.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 商品可适用的门店（多对多连接表）。表里没有记录 = 仅限商品所属门店。 */
@Entity
@Table(name = "product_venue", uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "venue_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ProductVenueEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "product_id", nullable = false)
    private Long productId;
    @Column(name = "venue_id", nullable = false)
    private Long venueId;
}
