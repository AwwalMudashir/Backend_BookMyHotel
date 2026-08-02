package com.project.Backend_BookMyHotel.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BranchResponseDto {
    private Long id;
    private String name;
    private Long hotelId;
    private String hotelName;
    private String city;
    private String country;
    private String address;
    private String currency;
    private LocalTime checkOutTime;
    private Boolean ecoCertified;
    private List<String> ecoTags;
    private Integer ecoScore;
}
