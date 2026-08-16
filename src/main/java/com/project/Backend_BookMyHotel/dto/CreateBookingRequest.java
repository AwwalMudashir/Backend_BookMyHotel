package com.project.Backend_BookMyHotel.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;

public record CreateBookingRequest(
        @NotNull(message = "Room ID is required")
        Long roomId,

        @NotNull(message = "Check-in date is required")
        @FutureOrPresent(message = "Check-in date cannot be in the past")
        LocalDate checkIn,

        @NotNull(message = "Check-out date is required")
        LocalDate checkOut,

        String promoCode,

        @Valid
        List<AddServicesRequest.ServiceItem> services,

        @Min(value = 0, message = "Eco points cannot be negative")
        Integer ecoPointsToRedeem
) {
    public CreateBookingRequest(Long roomId, LocalDate checkIn, LocalDate checkOut, String promoCode) {
        this(roomId, checkIn, checkOut, promoCode, List.of(), 0);
    }

    public CreateBookingRequest(Long roomId, LocalDate checkIn, LocalDate checkOut, String promoCode,
                                List<AddServicesRequest.ServiceItem> services) {
        this(roomId, checkIn, checkOut, promoCode, services, 0);
    }
}
