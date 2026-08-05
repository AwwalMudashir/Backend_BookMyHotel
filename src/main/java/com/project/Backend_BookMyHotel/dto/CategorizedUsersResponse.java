package com.project.Backend_BookMyHotel.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CategorizedUsersResponse {
    private List<UserDto> admins;
    private List<UserDto> hotelManagers;
    private List<UserDto> customers;
}
