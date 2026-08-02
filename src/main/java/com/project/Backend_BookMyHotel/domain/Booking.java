package com.project.Backend_BookMyHotel.domain;

import com.project.Backend_BookMyHotel.dto.BookingStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "bookings")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String reference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "check_in")
    private LocalDate checkIn;

    @Column(name = "check_out")
    private LocalDate checkOut;

    @Enumerated(EnumType.STRING)
    private BookingStatus status;

    // Nullable — most bookings won't have one. Set once at creation time so the discount baked
    // into totalPrice can always be traced back to the promo code that produced it.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id")
    private Promotion promotion;

    @Column(name = "total_price")
    private BigDecimal totalPrice;

    // How many eco points THIS booking contributed to its user, set once at confirmation time.
    // Tracked per-booking (rather than just incrementing User.ecoPoints directly) so cancelling a
    // CONFIRMED eco booking later can claw back exactly what it granted — without this, cancelling
    // after confirmation would leave the user with points for a stay that no longer happened.
    // @Builder.Default is required here: Lombok's builder otherwise silently ignores this field
    // initializer and leaves it null for anything built via Booking.builder()...build().
    @Builder.Default
    @Column(name = "eco_points_earned")
    private Integer ecoPointsEarned = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingAddonService> bookingServices;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL)
    private List<Payment> payments;
}