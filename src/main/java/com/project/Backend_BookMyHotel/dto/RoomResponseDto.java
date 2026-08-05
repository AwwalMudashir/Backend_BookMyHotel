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
public class RoomResponseDto {
    private Long roomNumber;
    private Long branchId;
    private String branchName;
    private BigDecimal pricePerNight;
    private String currency;
    private String roomTypeName;
    private Map<String, Object> amenities;
    private List<String> images;
    // Cloudinary image public IDs
    private List<String> publicIds;
    // Existing per-entity publicId (legacy) and the new public-facing roomId
    private String roomId;
    private Set<RoomTag> tags;
}
