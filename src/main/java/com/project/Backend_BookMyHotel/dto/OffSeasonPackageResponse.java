package com.project.Backend_BookMyHotel.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OffSeasonPackageResponse {
    private Long id;
    private PackageScope scope;
    private Long hotelId;
    private String hotelName;
    private Long branchId;
    private String branchName;
    private String code;
    private String name;
    private String summary;
    private String description;
    private List<String> inclusions;
    private List<String> eligibleRoomTypes;
    private String termsAndConditions;
    private String imageUrl;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private String discountCurrency;
    private BigDecimal maxDiscountAmount;
    private BigDecimal minimumRoomSubtotal;
    private LocalDate bookingStartDate;
    private LocalDate bookingEndDate;
    private LocalDate stayStartDate;
    private LocalDate stayEndDate;
    private Integer minimumNights;
    private Integer maximumNights;
    private Integer minimumAdvanceDays;
    private Integer maxBookings;
    private Integer timesBooked;
    private Integer remainingBookings;
    private Boolean featured;
    private Boolean active;
    private Long createdById;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
