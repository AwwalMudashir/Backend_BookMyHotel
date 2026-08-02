package com.project.Backend_BookMyHotel.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// Partial update: every field is optional, only non-null fields overwrite the existing promotion —
// same pattern as UpdateProfileDto. Use DELETE /promotions/{id} to deactivate rather than setting
// active=false here.
public record UpdatePromotionRequest(
        DiscountType discountType,
        BigDecimal discountValue,
        LocalDate validFrom,
        LocalDate validTo,
        Integer maxUses,
        BigDecimal minBookingAmount,
        BigDecimal maxDiscountAmount
) {}
