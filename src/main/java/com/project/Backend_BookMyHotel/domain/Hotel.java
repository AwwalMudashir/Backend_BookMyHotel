package com.project.Backend_BookMyHotel.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(name = "hotels")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Hotel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "star_rating")
    private Integer starRating;

    @Column(name = "logo_url")
    private String logoUrl;

    @OneToMany(mappedBy = "hotel", cascade = CascadeType.ALL)
    private List<Branch> branches;

    @OneToMany(mappedBy = "hotel", cascade = CascadeType.ALL)
    private List<Promotion> promotions;
}