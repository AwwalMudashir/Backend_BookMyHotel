package com.project.Backend_BookMyHotel.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "otps")
public class OtpVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // This serves as your "entry_id"

    @Column(nullable = false)
    private String email;

    @Column(nullable = true)
    private String otpValue;

    @Column(nullable = false)
    private LocalDateTime expiryTime;

    @Column(unique = true)
    private String resetToken;

    public OtpVerification(String email, String otpValue, LocalDateTime expiryTime) {
        this.email = email;
        this.otpValue = otpValue;
        this.expiryTime = expiryTime;
    }

    // Helper method to check if expired
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiryTime);
    }

    // Refresh OTP value and extend expiry time by 5 minutes
    public void refreshOtp(String newOtpValue, int expiryMinutes) {
        this.otpValue = newOtpValue;
        this.expiryTime = LocalDateTime.now().plusMinutes(expiryMinutes);
    }
}
