package com.project.Backend_BookMyHotel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SustainabilityTagRequest(
        Long hotelId,
        Long branchId,
        boolean allBranches,
        @NotBlank(message = "Name is required")
        @Size(max = 80, message = "Name cannot exceed 80 characters")
        String name,
        @Size(max = 500, message = "Description cannot exceed 500 characters")
        String description
) {}
