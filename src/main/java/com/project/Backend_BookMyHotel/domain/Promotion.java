package com.project.Backend_BookMyHotel.domain;

import com.project.Backend_BookMyHotel.dto.DiscountType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "promotions")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    @Builder.Default
    private DiscountType discountType = DiscountType.PERCENTAGE;

    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to", nullable = false)
    private LocalDate validTo;

    @Column(name = "max_uses")
    private Integer maxUses;

    @Builder.Default
    @Column(name = "times_used", nullable = false)
    private Integer timesUsed = 0;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    // Optional thresholds
    @Column(name = "min_booking_amount")
    private BigDecimal minBookingAmount;

    @Column(name = "max_discount_amount")
    private BigDecimal maxDiscountAmount; // e.g., cap percentage discount at $50
}