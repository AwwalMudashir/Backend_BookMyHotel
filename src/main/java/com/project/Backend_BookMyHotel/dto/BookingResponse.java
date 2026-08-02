package com.project.Backend_BookMyHotel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BookingResponse {
    private Long id;
    private Long roomId;
    private Long userId;
    private String reference;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private BookingStatus status;
    private BigDecimal totalPrice;
    // The promo code actually stored on the booking (Booking.promotion), independent of
    // priceBreakdown.appliedPromoCode below, which only ever populates on the create response.
    private String promoCode;
    // Only non-zero once the booking has been confirmed for an eco-friendly-tagged room — see
    // BookingService.confirmBooking. Always 0 on the create response itself.
    private Integer ecoPointsEarned;
    private PriceBreakdown priceBreakdown;
    private List<AddonServiceResponse> services;
    private LocalDateTime createdAt;

    @Data
    @Builder
    public static class PriceBreakdown {
        private BigDecimal basePrice;
        private BigDecimal discountAmount;
        private BigDecimal finalPrice;
        private String appliedPromoCode;
    }

    @Data
    @Builder
    public static class AddonServiceResponse {
        private Long id;
        private String serviceName;
        private Integer quantity;
        private BigDecimal subtotal;
    }
}
