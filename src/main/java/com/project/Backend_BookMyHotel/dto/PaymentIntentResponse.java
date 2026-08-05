package com.project.Backend_BookMyHotel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaymentIntentResponse {
    private Long bookingId;
    private String paymentId;
    private String paymentIntentId;
    // The only piece of this the frontend actually needs: hand it to Stripe.js
    // (stripe.confirmPayment / Payment Element) to complete the charge client-side.
    private String clientSecret;
    private BigDecimal amount;
    private String currency;
}
