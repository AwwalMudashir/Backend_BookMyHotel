package com.project.Backend_BookMyHotel.controller;

import com.project.Backend_BookMyHotel.dto.*;
import com.project.Backend_BookMyHotel.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private com.project.Backend_BookMyHotel.service.GoogleAuthService googleAuthService;

    @PostMapping("/register")
    public ResponseEntity<?> createUser(@RequestBody OnboardDto onboardDto){
        return userService.createUser(onboardDto);
    }

    @PostMapping("/register/hotel-manager")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> addHotelmanager(@RequestBody OnboardHotelManager onboardDto){
        return userService.addHotelManager(onboardDto);
    }

    @PostMapping("/register/admin")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> createAdmin(@RequestBody AdminDto onboardDto){
        return userService.createAdmin(onboardDto);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        return userService.login(request);
    }

    @PostMapping("/google")
    public ResponseEntity<?> googleLogin(@RequestBody GoogleLoginRequest request) {
        return googleAuthService.googleLogin(request);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody TokenRefreshRequest request) {
        return userService.refreshToken(request);
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
       return userService.getCurrentUser(authentication);
    }

    @GetMapping("/me/points")
    public ResponseEntity<?> getCurrentUserPoints(Authentication authentication) {
        return userService.getCurrentUserPoints(authentication);
    }



    @PutMapping("/me")
    public ResponseEntity<?> updateCurrentUser(Authentication authentication, @RequestBody UpdateProfileDto updateDto) {
       return userService.updateprofile(authentication,updateDto);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestParam String email) {
        return userService.forgotPassword(email);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody VerifyOtpDto verifyDto) {
        return userService.verifyOtp(verifyDto);
    }

    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestParam String userEmail) {
        return userService.sendOtp(userEmail);
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<?> resendOtp(@RequestParam String userEmail) {
       return userService.resendOtp(userEmail);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(Authentication authentication,@RequestBody ResetPasswordDto resetDto) {
       return userService.resetPassword(authentication,resetDto);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody TokenRefreshRequest request) {
        return userService.logout(request == null ? null : request.getRefreshToken());
    }

}
