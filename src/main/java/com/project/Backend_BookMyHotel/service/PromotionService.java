package com.project.Backend_BookMyHotel.service;

import com.project.Backend_BookMyHotel.domain.Promotion;
import com.project.Backend_BookMyHotel.dto.DiscountType;
import com.project.Backend_BookMyHotel.dto.PromotionBreakdownResponse;
import com.project.Backend_BookMyHotel.repository.PromotionRepository;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.NoSuchElementException;

@Service
public class PromotionService {

    @Autowired
    private PromotionRepository promotionRepository;

    // Validates a promo code and calculates the discount breakdown.
    @Transactional(readOnly = true)
    public PromotionBreakdownResponse applyPromotion(String code, BigDecimal totalPrice, Long hotelId) {
        // 1. Fetch promo code
        Promotion promo = promotionRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new NoSuchElementException("Invalid promotional code: " + code));

        // 2. Active status check
        if (Boolean.FALSE.equals(promo.getActive())) {
            return PromotionBreakdownResponse.builder()
                    .promoCode(null)
                    .discountType(null)
                    .discountValue(null)
                    .originalPrice(null)
                    .discountAmount(null)
                    .finalPrice(null)
                    .message("This promotional code is inactive.")
                    .build();
        }

        // 3. Expiry / Date Range Check (validFrom & validTo)
        LocalDate today = LocalDate.now();
        if (today.isBefore(promo.getValidFrom())) {
            return PromotionBreakdownResponse.builder()
                    .promoCode(null)
                    .discountType(null)
                    .discountValue(null)
                    .originalPrice(null)
                    .discountAmount(null)
                    .finalPrice(null)
                    .message("This promo code is not active yet. Valid from: " + promo.getValidFrom())
                    .build();
        }

        if (today.isAfter(promo.getValidTo())) {
            return PromotionBreakdownResponse.builder()
                    .promoCode(null)
                    .discountType(null)
                    .discountValue(null)
                    .originalPrice(null)
                    .discountAmount(null)
                    .finalPrice(null)
                    .message("This promo code expired on " + promo.getValidTo())
                    .build();
        }

        // 4. Usage Limit Check (maxUses & timesUsed)
        if (promo.getMaxUses() != null && promo.getTimesUsed() >= promo.getMaxUses()) {
            return PromotionBreakdownResponse.builder()
                    .promoCode(null)
                    .discountType(null)
                    .discountValue(null)
                    .originalPrice(null)
                    .discountAmount(null)
                    .finalPrice(null)
                    .message("This promo code has reached its maximum usage limit.")
                    .build();

        }

        // 5. Hotel Specificity Check
        if (promo.getHotel() != null) {
            if (!promo.getHotel().getId().equals(hotelId)) {
                return PromotionBreakdownResponse.builder()
                        .promoCode(null)
                        .discountType(null)
                        .discountValue(null)
                        .originalPrice(null)
                        .discountAmount(null)
                        .finalPrice(null)
                        .message("This promo code is not valid for the selected hotel.")
                        .build();
            }
        }

        // 6. Minimum Spend Check
        if (promo.getMinBookingAmount() != null && totalPrice.compareTo(promo.getMinBookingAmount()) < 0) {
            return PromotionBreakdownResponse.builder()
                    .promoCode(null)
                    .discountType(null)
                    .discountValue(null)
                    .originalPrice(null)
                    .discountAmount(null)
                    .finalPrice(null)
                    .message("Minimum Booking amount cannot be less than 0")
                    .build();
        }

        // 7. Calculate Discount
        BigDecimal discountAmount = calculateDiscount(promo, totalPrice);

        // Ensure discount cannot exceed the original total
        if (discountAmount.compareTo(totalPrice) > 0) {
            discountAmount = totalPrice;
        }

        BigDecimal finalPrice = totalPrice.subtract(discountAmount).setScale(2, RoundingMode.HALF_UP);

        return PromotionBreakdownResponse.builder()
                .promoCode(promo.getCode().toUpperCase())
                .discountType(promo.getDiscountType())
                .discountValue(promo.getDiscountValue())
                .originalPrice(totalPrice.setScale(2, RoundingMode.HALF_UP))
                .discountAmount(discountAmount.setScale(2, RoundingMode.HALF_UP))
                .finalPrice(finalPrice)
                .message("Promo code successfully applied!")
                .build();
    }

    // Increments the timesUsed counter when a booking is confirmed.
    @Transactional
    public void incrementPromotionUsage(String code) {
        promotionRepository.findByCodeIgnoreCase(code).ifPresent(promo -> {
            int currentUses = promo.getTimesUsed() != null ? promo.getTimesUsed() : 0;
            promo.setTimesUsed(currentUses + 1);
            promotionRepository.save(promo);
        });
    }

    private BigDecimal calculateDiscount(Promotion promo, BigDecimal totalPrice) {
        if (promo.getDiscountType() == DiscountType.PERCENTAGE) {
            // totalPrice * (discountValue / 100)
            BigDecimal percentage = promo.getDiscountValue().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            BigDecimal calculated = totalPrice.multiply(percentage);

            if (promo.getMaxDiscountAmount() != null && calculated.compareTo(promo.getMaxDiscountAmount()) > 0) {
                return promo.getMaxDiscountAmount();
            }
            return calculated;
        } else {
            // FIXED_AMOUNT (e.g. $20 off)
            return promo.getDiscountValue();
        }
    }
}