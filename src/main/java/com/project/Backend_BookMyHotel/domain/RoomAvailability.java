package com.project.Backend_BookMyHotel.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "room_availability")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoomAvailability {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    private LocalDate date;

    @Column(name = "is_available")
    private Boolean isAvailable;

    @Column(name = "daily_rate")
    private BigDecimal dailyRate;

    @Column(name = "occupancy_count")
    private Integer occupancyCount;
}
