package com.project.Backend_BookMyHotel.controller;

import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.LoginRequest;
import com.project.Backend_BookMyHotel.dto.OnboardDto;
import com.project.Backend_BookMyHotel.dto.TokenRefreshRequest;
import com.project.Backend_BookMyHotel.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserService userService;

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
}
