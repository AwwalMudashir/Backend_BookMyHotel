package com.project.Backend_BookMyHotel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RoomResponse {
    private Long id;
    // New: public-facing random identifier for the room (room.roomId)
    private String roomId;
    private Long branchId;
    private String roomType;
    private String description;
    private Integer maxOccupancy;
    private BigDecimal pricePerNight;
    private String currency;
    private Map<String, Object> amenities;
    private List<String> images;
    // Cloudinary image public IDs used to delete images
    private List<String> publicIds;
    private Set<RoomTag> tags;
}
