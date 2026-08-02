package com.project.Backend_BookMyHotel.domain;

import com.project.Backend_BookMyHotel.dto.RoomTag;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "rooms")
@Data
@AllArgsConstructor
@NoArgsConstructor
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

    // Using a simple String representation for JSONB natively supported by many modern dialects.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "amenities", columnDefinition = "jsonb")
    private Map<String, Object> amenities;

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL)
    private List<RoomAvailability> availabilities;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "images", columnDefinition = "jsonb")
    private List<String> images = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "public_ids", columnDefinition = "jsonb")
    private List<String> publicIds = new ArrayList<>();

    // A proper, filterable classification set (eco-friendly, work-friendly, ...) — kept separate
    // from `amenities` above since these are curated category badges the search/UI need to query
    // directly, not arbitrary feature flags. Backed by its own join table (room_tags) rather than
    // another JSON column so RoomSpecification can filter on it with a normal SQL join.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "room_tags", joinColumns = @JoinColumn(name = "room_id"))
    @Column(name = "tag")
    @Enumerated(EnumType.STRING)
    private Set<RoomTag> tags = new HashSet<>();
}
