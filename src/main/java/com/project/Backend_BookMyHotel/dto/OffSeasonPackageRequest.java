package com.project.Backend_BookMyHotel.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record OffSeasonPackageRequest(
        @NotNull(message = "Package scope is required")
        PackageScope scope,

        Long hotelId,
        Long branchId,

        @NotBlank(message = "Package code is required")
        @Pattern(regexp = "[A-Za-z0-9_-]{3,40}", message = "Package code must be 3-40 letters, numbers, underscores or hyphens")
        String code,

        @NotBlank(message = "Package name is required")
        @Size(max = 120, message = "Package name must not exceed 120 characters")
        String name,

        @NotBlank(message = "Package summary is required")
        @Size(max = 280, message = "Package summary must not exceed 280 characters")
        String summary,

        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description,

        @Size(max = 12, message = "A package can contain at most 12 inclusions")
        List<@Size(max = 120, message = "Each inclusion must not exceed 120 characters") String> inclusions,

        @Size(max = 12, message = "A package can target at most 12 room types")
        List<@Size(max = 80, message = "Each room type must not exceed 80 characters") String> eligibleRoomTypes,

        @Size(max = 2500, message = "Terms must not exceed 2500 characters")
        String termsAndConditions,

        @Size(max = 1000, message = "Image URL must not exceed 1000 characters")
        String imageUrl,

        @NotNull(message = "Discount type is required")
        DiscountType discountType,

        @NotNull(message = "Discount value is required")
        @Positive(message = "Discount value must be greater than zero")
        BigDecimal discountValue,

        @NotBlank(message = "Discount currency is required")
        @Pattern(regexp = "[A-Za-z]{3}", message = "Discount currency must be a 3-letter ISO code")
        String discountCurrency,

        @Positive(message = "Maximum discount must be greater than zero")
        BigDecimal maxDiscountAmount,

        @PositiveOrZero(message = "Minimum room subtotal cannot be negative")
        BigDecimal minimumRoomSubtotal,

        @NotNull(message = "Booking start date is required")
        LocalDate bookingStartDate,

        @NotNull(message = "Booking end date is required")
        LocalDate bookingEndDate,

        @NotNull(message = "Stay start date is required")
        LocalDate stayStartDate,

        @NotNull(message = "Stay end date is required")
        LocalDate stayEndDate,

        @NotNull(message = "Minimum nights is required")
        @Min(value = 1, message = "Minimum nights must be at least one")
        Integer minimumNights,

        @Min(value = 1, message = "Maximum nights must be at least one")
        Integer maximumNights,

        @NotNull(message = "Minimum advance days is required")
        @Min(value = 0, message = "Minimum advance days cannot be negative")
        Integer minimumAdvanceDays,

        @Positive(message = "Maximum bookings must be greater than zero")
        Integer maxBookings,

        Boolean featured
) {}
