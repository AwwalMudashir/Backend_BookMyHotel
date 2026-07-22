package com.project.Backend_BookMyHotel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RoomResponse {
    private Long id;
    private Long branchId;
    private String roomType;
    private String description;
    private Integer maxOccupancy;
    private BigDecimal pricePerNight;
    private String currency;
    private Map<String, Object> amenities;
    private List<String> images;
    private List<String> publicIds;
}
