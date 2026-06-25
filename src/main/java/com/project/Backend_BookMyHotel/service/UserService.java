package com.project.Backend_BookMyHotel.service;

import com.project.Backend_BookMyHotel.domain.RefreshToken;
import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.*;
import com.project.Backend_BookMyHotel.repository.UserRepository;
import com.project.Backend_BookMyHotel.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RefreshTokenService refreshTokenService;

    private BCryptPasswordEncoder bencoder = new BCryptPasswordEncoder(12);

    public String generateRandomId(){
        Random random = new Random();
        return "BMH" + (random.nextInt() * 999999);
    }


    public ResponseEntity<?> createUser(OnboardDto onboardDto) {
        User user = new User();

        if (userRepo.existsByEmail(onboardDto.getEmail())){
            return new ResponseEntity<>("User already Exists", HttpStatus.BAD_REQUEST);
        }

        user.setFirstName(onboardDto.getFirstName());
        user.setLastName(onboardDto.getLastName());
        user.setEmail(onboardDto.getEmail());
        user.setPassword(bencoder.encode(onboardDto.getPassword()));
        user.setRole(onboardDto.getRole());

        String userId = generateRandomId();

        try {
            while (userRepo.existsByUserId(userId)){
                userId = generateRandomId();
            }
        } catch (Exception e) {
            return new ResponseEntity<>("Error generating a random user ID", HttpStatus.CONFLICT);
        }

        user.setUserId(userId);
        userRepo.save(user);

        // Sending of Registration Email

        return new ResponseEntity<>(user, HttpStatus.CREATED);
    }

    public ResponseEntity<?> login(LoginRequest request) {
        try {
            // Authenticate user
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        }

        User user = userRepo.findByEmail(request.getEmail());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not found");
        }

        // Generate JWT token
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getUserId());


        // Return JWT response
        JwtResponse jwtResponse = new JwtResponse(
                token,
                refreshToken.getToken(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole()
        );

        return ResponseEntity.ok(jwtResponse);
    }

    public ResponseEntity<?> refreshToken(TokenRefreshRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    String token = jwtUtil.generateToken(user.getEmail(), user.getRole());
                    return ResponseEntity.ok(new TokenRefreshResponse(token, requestRefreshToken));
                })
                .orElseThrow(() -> new RuntimeException("Refresh token is not in database or expired!"));
    }
}
