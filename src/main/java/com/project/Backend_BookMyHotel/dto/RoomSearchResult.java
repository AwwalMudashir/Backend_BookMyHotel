package com.project.Backend_BookMyHotel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomSearchResult {
    private Long roomId;
    private String hotelName;
    private String branchCity;
    private String roomType;
    private BigDecimal pricePerNight;
    private BigDecimal totalPrice;
    private Map<String, Object> amenities;
    private boolean available;
}