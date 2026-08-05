package com.project.Backend_BookMyHotel.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class HotelAnalyticsResponse {
    private Long hotelId;
    private String hotelName;
    private LocalDate startDate;
    private LocalDate endDate;
    private long roomNightsBooked;
    private BigDecimal revenue;
    private BigDecimal averageDailyRate;
    private String currency;
}
