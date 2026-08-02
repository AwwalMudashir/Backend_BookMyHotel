package com.project.Backend_BookMyHotel.service;

import com.project.Backend_BookMyHotel.domain.Hotel;
import com.project.Backend_BookMyHotel.domain.Promotion;
import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.CreatePromotionRequest;
import com.project.Backend_BookMyHotel.dto.DiscountType;
import com.project.Backend_BookMyHotel.dto.PromotionBreakdownResponse;
import com.project.Backend_BookMyHotel.dto.PromotionResponse;
import com.project.Backend_BookMyHotel.dto.Role;
import com.project.Backend_BookMyHotel.dto.UpdatePromotionRequest;
import com.project.Backend_BookMyHotel.repository.HotelRepository;
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
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class PromotionService {

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private HotelRepository hotelRepository;

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

    @Transactional
    public ResponseEntity<?> createPromotion(CreatePromotionRequest request, User actor) {
        Hotel hotel = hotelRepository.findById(request.hotelId())
                .orElseThrow(() -> new NoSuchElementException("Hotel not found with ID: " + request.hotelId()));

        if (!hasHotelAccess(actor, hotel.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You do not manage this hotel.");
        }

        if (promotionRepository.findByCodeIgnoreCase(request.code()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("A promotion with code " + request.code() + " already exists.");
        }

        if (request.validTo().isBefore(request.validFrom())) {
            return ResponseEntity.badRequest().body("validTo cannot be before validFrom.");
        }

        Promotion promo = Promotion.builder()
                .hotel(hotel)
                .code(request.code().trim().toUpperCase())
                .discountType(request.discountType())
                .discountValue(request.discountValue())
                .validFrom(request.validFrom())
                .validTo(request.validTo())
                .maxUses(request.maxUses())
                .minBookingAmount(request.minBookingAmount())
                .maxDiscountAmount(request.maxDiscountAmount())
                .build();

        Promotion saved = promotionRepository.save(promo);
        return ResponseEntity.status(HttpStatus.CREATED).body(toPromotionResponse(saved));
    }

    @Transactional
    public ResponseEntity<?> updatePromotion(Long promotionId, UpdatePromotionRequest request, User actor) {
        Promotion promo = promotionRepository.findById(promotionId)
                .orElseThrow(() -> new NoSuchElementException("Promotion not found with ID: " + promotionId));

        if (!hasHotelAccess(actor, promo.getHotel().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You do not manage this hotel.");
        }

        if (request.discountType() != null) {
            promo.setDiscountType(request.discountType());
        }
        if (request.discountValue() != null) {
            promo.setDiscountValue(request.discountValue());
        }
        if (request.validFrom() != null) {
            promo.setValidFrom(request.validFrom());
        }
        if (request.validTo() != null) {
            promo.setValidTo(request.validTo());
        }
        if (promo.getValidTo().isBefore(promo.getValidFrom())) {
            return ResponseEntity.badRequest().body("validTo cannot be before validFrom.");
        }
        if (request.maxUses() != null) {
            promo.setMaxUses(request.maxUses());
        }
        if (request.minBookingAmount() != null) {
            promo.setMinBookingAmount(request.minBookingAmount());
        }
        if (request.maxDiscountAmount() != null) {
            promo.setMaxDiscountAmount(request.maxDiscountAmount());
        }

        Promotion saved = promotionRepository.save(promo);
        return ResponseEntity.ok(toPromotionResponse(saved));
    }

    // Deactivates rather than deletes — a promo already applied to past bookings (Booking.promotion)
    // must keep existing so those bookings' history/receipts still resolve correctly.
    @Transactional
    public ResponseEntity<?> deactivatePromotion(Long promotionId, User actor) {
        Promotion promo = promotionRepository.findById(promotionId)
                .orElseThrow(() -> new NoSuchElementException("Promotion not found with ID: " + promotionId));

        if (!hasHotelAccess(actor, promo.getHotel().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You do not manage this hotel.");
        }

        promo.setActive(false);
        promotionRepository.save(promo);
        return ResponseEntity.ok(toPromotionResponse(promo));
    }

    public ResponseEntity<?> listActivePromotions(Long hotelId) {
        if (!hotelRepository.existsById(hotelId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Hotel not found with ID: " + hotelId);
        }

        List<PromotionResponse> promotions = promotionRepository.findByHotelIdAndActiveTrue(hotelId).stream()
                .map(this::toPromotionResponse)
                .toList();

        return ResponseEntity.ok(promotions);
    }

    // ADMIN can manage promotions for any hotel; a HOTEL_MANAGER only for the hotel they manage —
    // same ownership rule ServiceService.createService uses for services.
    private boolean hasHotelAccess(User actor, Long hotelId) {
        if (actor.getRole() == Role.ADMIN) {
            return true;
        }
        return actor.getManagedHotel() != null && actor.getManagedHotel().getId().equals(hotelId);
    }

    private PromotionResponse toPromotionResponse(Promotion promo) {
        return PromotionResponse.builder()
                .id(promo.getId())
                .hotelId(promo.getHotel().getId())
                .hotelName(promo.getHotel().getName())
                .code(promo.getCode())
                .discountType(promo.getDiscountType())
                .discountValue(promo.getDiscountValue())
                .validFrom(promo.getValidFrom())
                .validTo(promo.getValidTo())
                .maxUses(promo.getMaxUses())
                .timesUsed(promo.getTimesUsed())
                .active(promo.getActive())
                .minBookingAmount(promo.getMinBookingAmount())
                .maxDiscountAmount(promo.getMaxDiscountAmount())
                .build();
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