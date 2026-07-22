package com.project.Backend_BookMyHotel.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BranchRequestDto {
    private String name;
    private String city;
    private String country;
    private String address;
    @NotBlank
    @Size(min = 3, max = 3)
    private String currency;
    private LocalTime checkInTime;
    private LocalTime checkOutTime;
}
