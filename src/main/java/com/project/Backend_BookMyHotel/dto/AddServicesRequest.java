package com.project.Backend_BookMyHotel.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AddServicesRequest(
        @NotEmpty(message = "At least one service is required")
        @Valid
        List<ServiceItem> services
) {
    public record ServiceItem(
            @NotNull(message = "Service ID is required")
            Long serviceId,

            @NotNull(message = "Quantity is required")
            @Min(value = 1, message = "Quantity must be at least 1")
            Integer quantity
    ) {}
}
