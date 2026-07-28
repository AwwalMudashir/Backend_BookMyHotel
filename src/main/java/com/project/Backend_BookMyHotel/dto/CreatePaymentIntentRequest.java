package com.project.Backend_BookMyHotel.dto;

import jakarta.validation.constraints.NotNull;

public record CreatePaymentIntentRequest(
        @NotNull(message = "Booking ID is required")
        Long bookingId
) {}
