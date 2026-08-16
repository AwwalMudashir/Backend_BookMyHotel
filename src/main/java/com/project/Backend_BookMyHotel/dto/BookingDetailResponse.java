package com.project.Backend_BookMyHotel.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class BookingDetailResponse {
    private Long id;
    private Long roomId;
    // Public-facing random identifier for a room (room.roomId). Included for frontend convenience.
    private String roomPublicId;
    private String roomNumber;
    private String hotelName;
    private Long userId;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private BookingStatus status;
    private BigDecimal totalPrice;
    private String promoCode;
    private Integer ecoPointsEarned;
    private Integer ecoPointsRedeemed;
    private BigDecimal ecoPointsDiscount;
    private LocalDateTime createdAt;
    private List<AddonServiceResponse> services;
    private List<PaymentResponse> payments;

    @Data
    @Builder
    public static class AddonServiceResponse {
        private Long id;
        private Long serviceId;
        private String serviceName;
        private BigDecimal unitPrice;
        private Integer quantity;
        private BigDecimal subtotal;
    }

    @Data
    @Builder
    public static class PaymentResponse {
        private Long id;
        private String stripePaymentId;
        private BigDecimal amount;
        private String currency;
        private PaymentStatus status;
        private String refundId;
        private LocalDateTime paidAt;
    }
}
