package com.project.Backend_BookMyHotel.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalTime;
import java.util.List;

@Entity
@Table(name = "branches")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Branch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    private String city;
    private String country;
    private String address;

    @Column(nullable = false, length = 3)
    private String currency;

    private String name;

    @Column(name = "check_in_time")
    private LocalTime checkInTime;

    @Column(name = "check_out_time")
    private LocalTime checkOutTime;

    // Property-level sustainability profile — distinct from Room.tags (RoomTag.ECO_FRIENDLY),
    // which drives the per-booking eco-points reward. This describes the branch itself: whether
    // it holds a formal certification, which sustainable practices it follows, and an overall
    // score — not derivable from which individual rooms happen to be tagged eco-friendly.
    @Column(name = "eco_certified")
    private Boolean ecoCertified = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "eco_tags", columnDefinition = "jsonb")
    private List<String> ecoTags;

    @Column(name = "eco_score")
    private Integer ecoScore;

    @OneToMany(mappedBy = "branch", cascade = CascadeType.ALL)
    private List<Room> rooms;

    @OneToMany(mappedBy = "branch", cascade = CascadeType.ALL)
    private List<Service> services;

    @OneToMany(mappedBy = "branch", cascade = CascadeType.ALL)
    private List<Review> reviews;
}
