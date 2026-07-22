package com.project.Backend_BookMyHotel.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReviewResponse {
    private Long id;
    private Long branchId;
    private Long userId;
    private String reviewerName;   // first name + last name from User
    private Integer rating;
    private String comment;
    private Boolean isVerified;
    private LocalDateTime createdAt;
}
