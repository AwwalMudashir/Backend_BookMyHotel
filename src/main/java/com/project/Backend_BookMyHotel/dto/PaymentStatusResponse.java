package com.project.Backend_BookMyHotel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaymentStatusResponse {
    private Long bookingId;
    private String stripePaymentId;
    private PaymentStatus status;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime paidAt;
    private String refundId;
}
