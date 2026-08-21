package com.project.Backend_BookMyHotel.dto;

import lombok.Builder;

@Builder
public record SustainabilityTagResponse(
        Long id,
        Long hotelId,
        String hotelName,
        Long branchId,
        String branchName,
        boolean allBranches,
        String name,
        String description,
        boolean active
) {}
