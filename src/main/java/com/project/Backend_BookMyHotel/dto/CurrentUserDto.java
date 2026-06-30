package com.project.Backend_BookMyHotel.dto;

import com.project.Backend_BookMyHotel.domain.Booking;
import com.project.Backend_BookMyHotel.domain.Review;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CurrentUserDto {
    private String userId;
    private String email;
    private String firstName;
    private String lastName;
    private Role role;
    private List<Booking> bookings;
    private List<Review> reviews;
}
