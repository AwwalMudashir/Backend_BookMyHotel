package com.project.Backend_BookMyHotel.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResetPasswordDto {
    private String oldPassword;
    private String newPassword;
    private String confirmPassword;
    private String email;
    private String resetToken;
}
