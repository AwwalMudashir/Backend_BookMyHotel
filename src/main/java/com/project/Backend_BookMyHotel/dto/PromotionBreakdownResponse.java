package com.project.Backend_BookMyHotel.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PromotionBreakdownResponse {
    private Boolean isError;
    private String promoCode;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal originalPrice;
    private BigDecimal discountAmount;
    private BigDecimal finalPrice;
    private String message;
}