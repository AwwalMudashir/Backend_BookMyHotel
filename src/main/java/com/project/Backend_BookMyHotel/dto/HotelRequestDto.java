package com.project.Backend_BookMyHotel.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HotelRequestDto {
    private String name;
    private String description;
    private Integer starRating;
    private String logoUrl;
}
