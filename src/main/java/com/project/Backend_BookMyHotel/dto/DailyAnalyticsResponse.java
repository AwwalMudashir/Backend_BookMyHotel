package com.project.Backend_BookMyHotel.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyAnalyticsResponse(
        LocalDate date,
        long roomNightsBooked,
        BigDecimal revenue,
        BigDecimal averageDailyRate,
        String currency
) {}
