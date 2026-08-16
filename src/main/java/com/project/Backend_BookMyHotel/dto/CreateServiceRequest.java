package com.project.Backend_BookMyHotel.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateServiceRequest(
        Long hotelId,

        Long branchId,

        boolean allBranches,

        @NotBlank(message = "Name is required")
        String name,

        String description,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "Price cannot be negative")
        BigDecimal price,

        @NotNull(message = "Service type is required")
        ServiceType serviceType
) {
    // Keeps older callers source-compatible while the UI moves to explicit hotel/scope fields.
    public CreateServiceRequest(Long branchId, String name, String description,
                                BigDecimal price, ServiceType serviceType) {
        this(null, branchId, false, name, description, price, serviceType);
    }
}
