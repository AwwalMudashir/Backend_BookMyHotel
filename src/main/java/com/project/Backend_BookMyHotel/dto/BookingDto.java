package com.project.Backend_BookMyHotel.dto;

import com.project.Backend_BookMyHotel.dto.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookingDto {
    private Long id;
    private String reference;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private BookingStatus status;
    private BigDecimal totalPrice;
    private Integer ecoPointsEarned;
    private Integer ecoPointsRedeemed;
    private BigDecimal ecoPointsDiscount;
    private LocalDateTime createdAt;
}
