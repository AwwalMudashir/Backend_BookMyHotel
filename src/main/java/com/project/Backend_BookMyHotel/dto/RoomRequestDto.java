package com.project.Backend_BookMyHotel.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoomRequestDto {
    private BigDecimal pricePerNight;
    private Long roomTypeId;
    private String roomType;
    private String description;
    private Integer maxOccupancy;
    private Map<String, Object> amenities;
    private Set<RoomTag> tags;
    // Optional image URLs provided by admin (external images). These are appended to the room's image gallery
    private List<String> imageUrls;
    // Optional per-room currency (ISO-4217). If provided, persisted; otherwise branch currency is used/shown.
    private String currency;
}
