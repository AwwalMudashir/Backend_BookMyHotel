package com.project.Backend_BookMyHotel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreatePromotionRequest(
        @NotNull(message = "Hotel ID is required")
        Long hotelId,

        @NotBlank(message = "Promo code is required")
        String code,

        @NotNull(message = "Discount type is required")
        DiscountType discountType,

        @NotNull(message = "Discount value is required")
        @Positive(message = "Discount value must be greater than 0")
        BigDecimal discountValue,

        @NotNull(message = "Valid-from date is required")
        LocalDate validFrom,

        @NotNull(message = "Valid-to date is required")
        LocalDate validTo,

        Integer maxUses,
        BigDecimal minBookingAmount,
        BigDecimal maxDiscountAmount
) {}
