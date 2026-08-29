package com.project.Backend_BookMyHotel.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class OffSeasonPackageQuoteResponse {
    private Long packageId;
    private String packageCode;
    private String packageName;
    private Boolean eligible;
    private String message;
    private BigDecimal originalRoomPrice;
    private BigDecimal discountAmount;
    private BigDecimal roomPriceAfterDiscount;
    private String currency;
}
