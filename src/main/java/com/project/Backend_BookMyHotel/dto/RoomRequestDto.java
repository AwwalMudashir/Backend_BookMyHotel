package com.project.Backend_BookMyHotel.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoomRequestDto {
    private BigDecimal pricePerNight;
    private Long roomTypeId;
    private Map<String, Object> amenities;
    private Set<RoomTag> tags;
}
