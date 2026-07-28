package com.project.Backend_BookMyHotel.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OnboardHotelManager {
    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private Long hotelId;
}
