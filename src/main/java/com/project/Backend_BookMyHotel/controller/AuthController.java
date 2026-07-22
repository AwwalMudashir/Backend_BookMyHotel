package com.project.Backend_BookMyHotel.controller;

import com.project.Backend_BookMyHotel.domain.OtpVerification;
import com.project.Backend_BookMyHotel.domain.RefreshToken;
import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.*;
import com.project.Backend_BookMyHotel.repository.RefreshTokenRepository;
import com.project.Backend_BookMyHotel.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepo;

    @PostMapping("/register")
    public ResponseEntity<?> createUser(@RequestBody OnboardDto onboardDto){
        return userService.createUser(onboardDto);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        return userService.login(request);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody TokenRefreshRequest request) {
        return userService.refreshToken(request);
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
       return userService.getCurrentUser(authentication);
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
    public ResponseEntity<?> logout(@RequestParam String refreshToken) {
        return userService.logout(refreshToken);
    }

    @GetMapping("/refreshes")
    public List<RefreshToken> allrefreshTokens(){
        return refreshTokenRepo.findAll();
    }


}
