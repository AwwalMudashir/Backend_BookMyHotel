package com.project.Backend_BookMyHotel.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HotelDetailDto {
    private Long id;
    private String name;
    private String description;
    private Integer starRating;
    private String logoUrl;
    private List<BranchSummaryDto> branches;
}
