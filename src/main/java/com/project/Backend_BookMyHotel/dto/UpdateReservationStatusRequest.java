package com.project.Backend_BookMyHotel.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateReservationStatusRequest(
        @NotNull(message = "Status is required")
        BookingStatus status
) {}
