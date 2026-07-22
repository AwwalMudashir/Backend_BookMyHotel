package com.project.Backend_BookMyHotel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BranchResponse {
    private Long id;
    private String name;
    private Long hotelId;
    private String city;
    private String country;
    private String address;
    private String currency;
    private LocalTime checkInTime;
    private LocalTime checkOutTime;
}
