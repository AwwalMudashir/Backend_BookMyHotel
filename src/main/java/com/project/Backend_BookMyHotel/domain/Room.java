package com.project.Backend_BookMyHotel.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "rooms")
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "room_type")
    private String roomType;

    private String description;

    @Column(name = "max_occupancy")
    private Integer maxOccupancy;

    @Column(name = "price_per_night")
    private BigDecimal pricePerNight;

    private String currency;

    // Using a simple String representation for JSONB natively supported by many modern dialects.
    @Column(columnDefinition = "jsonb")
    private String amenities;

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL)
    private List<RoomAvailability> availabilities;
}
