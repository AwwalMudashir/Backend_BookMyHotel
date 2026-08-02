package com.project.Backend_BookMyHotel.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class PromotionResponse {
    private Long id;
    private Long hotelId;
    private String hotelName;
    private String code;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private LocalDate validFrom;
    private LocalDate validTo;
    private Integer maxUses;
    private Integer timesUsed;
    private Boolean active;
    private BigDecimal minBookingAmount;
    private BigDecimal maxDiscountAmount;
}
