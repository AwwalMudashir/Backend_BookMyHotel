package com.project.Backend_BookMyHotel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomPriceResponse {
    private Long roomId;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private long totalNights;
    private BigDecimal totalPrice;
    private String currency;
    private String targetCurrency;
    @JsonProperty("isAvailable")
    @Getter(onMethod_ = @JsonProperty("isAvailable"))
    private boolean isAvailable; // true only if EVERY night in range is available
    private List<NightlyPriceBreakdown> breakdown;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NightlyPriceBreakdown {
        private LocalDate date;
        private BigDecimal price;
        @JsonProperty("isAvailable")
        @Getter(onMethod_ = @JsonProperty("isAvailable"))
        private boolean isAvailable;
    }
}
