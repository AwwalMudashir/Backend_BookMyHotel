package com.project.Backend_BookMyHotel.domain;

import com.project.Backend_BookMyHotel.dto.DiscountType;
import com.project.Backend_BookMyHotel.dto.PackageScope;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "off_season_packages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OffSeasonPackage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PackageScope scope;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id")
    private Hotel hotel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 280)
    private String summary;

    @Column(length = 2000)
    private String description;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<String> inclusions = new ArrayList<>();

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "eligible_room_types", columnDefinition = "jsonb", nullable = false)
    private List<String> eligibleRoomTypes = new ArrayList<>();

    @Column(name = "terms_and_conditions", length = 2500)
    private String termsAndConditions;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 30)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Builder.Default
    @Column(name = "discount_currency", nullable = false, length = 3)
    private String discountCurrency = "USD";

    @Column(name = "max_discount_amount", precision = 12, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(name = "minimum_room_subtotal", precision = 12, scale = 2)
    private BigDecimal minimumRoomSubtotal;

    @Column(name = "booking_start_date", nullable = false)
    private LocalDate bookingStartDate;

    @Column(name = "booking_end_date", nullable = false)
    private LocalDate bookingEndDate;

    @Column(name = "stay_start_date", nullable = false)
    private LocalDate stayStartDate;

    @Column(name = "stay_end_date", nullable = false)
    private LocalDate stayEndDate;

    @Builder.Default
    @Column(name = "minimum_nights", nullable = false)
    private Integer minimumNights = 1;

    @Column(name = "maximum_nights")
    private Integer maximumNights;

    @Builder.Default
    @Column(name = "minimum_advance_days", nullable = false)
    private Integer minimumAdvanceDays = 0;

    @Column(name = "max_bookings")
    private Integer maxBookings;

    @Builder.Default
    @Column(name = "times_booked", nullable = false)
    private Integer timesBooked = 0;

    @Builder.Default
    @Column(nullable = false)
    private Boolean featured = false;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = now;
        inclusions = inclusions == null ? new ArrayList<>() : inclusions;
        eligibleRoomTypes = eligibleRoomTypes == null ? new ArrayList<>() : eligibleRoomTypes;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
