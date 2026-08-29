package com.project.Backend_BookMyHotel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OffSeasonPackageQuoteRequest(
        @NotNull(message = "Package ID is required") Long packageId,
        @NotNull(message = "Room ID is required") Long roomId,
        @NotNull(message = "Check-in date is required") LocalDate checkIn,
        @NotNull(message = "Check-out date is required") LocalDate checkOut,
        @NotNull(message = "Room subtotal is required")
        @PositiveOrZero(message = "Room subtotal cannot be negative") BigDecimal roomSubtotal,
        @NotBlank(message = "Currency is required") String currency
) {}
