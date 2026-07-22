package com.project.Backend_BookMyHotel.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SetRoomAvailabilityRequest(
        @NotNull(message = "Start date is required")
        LocalDate startDate,
        @NotNull(message = "End date is required")
        LocalDate endDate,
        Boolean isAvailable,
        BigDecimal customPrice,
        String reason
) {}
