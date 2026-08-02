package com.project.Backend_BookMyHotel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BranchReviewsResponse {
    private List<ReviewResponse> reviews;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private BigDecimal averageRating;
}
