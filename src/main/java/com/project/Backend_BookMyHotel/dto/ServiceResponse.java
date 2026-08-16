package com.project.Backend_BookMyHotel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ServiceResponse {
    private Long id;
    private Long hotelId;
    private String hotelName;
    private Long branchId;
    private String branchName;
    private Boolean allBranches;
    private String name;
    private String description;
    private BigDecimal price;
    private ServiceType serviceType;
    private Boolean active;
}
