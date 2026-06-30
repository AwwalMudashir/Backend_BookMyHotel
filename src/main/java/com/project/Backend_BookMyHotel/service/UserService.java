package com.project.Backend_BookMyHotel.service;

import com.project.Backend_BookMyHotel.domain.OtpVerification;
import com.project.Backend_BookMyHotel.domain.RefreshToken;
import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.*;
import com.project.Backend_BookMyHotel.repository.OtpRepository;
import com.project.Backend_BookMyHotel.repository.UserRepository;
import com.project.Backend_BookMyHotel.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private OtpRepository otpRepo;

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

    private String generateSixDigitOtp() {
        return String.format("%06d", new java.util.Random().nextInt(999999));
    }

    // TODO: ADD EMAIL INTEGRATION
    private void sendOtpEmail(String email, String otp) {
        // TODO: Integrate your actual Email Sender service here
        return;
    }

    public ResponseEntity<?> createUser(OnboardDto onboardDto) {
        User user = new User();
        ErrorResponse err = new ErrorResponse();

        if (userRepo.existsByEmail(onboardDto.getEmail())){
            err.setStatus(HttpStatus.BAD_REQUEST);
            err.setMessage("User already Exists");
            return new ResponseEntity<>(err, HttpStatus.BAD_REQUEST);
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
            err.setStatus(HttpStatus.BAD_REQUEST);
            err.setMessage("Error generating a random user ID");
            return new ResponseEntity<>(err, HttpStatus.CONFLICT);
        }

        user.setUserId(userId);
        userRepo.save(user);

        // Sending of Registration Email

        return new ResponseEntity<>(user, HttpStatus.CREATED);
    }

    public ResponseEntity<?> login(LoginRequest request) {
        ErrorResponse err = new ErrorResponse();

        try {
            // Authenticate user
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (Exception e) {
            err.setStatus(HttpStatus.UNAUTHORIZED);
            err.setMessage("User already Exists");
            return new ResponseEntity<>(err,HttpStatus.UNAUTHORIZED);
        }

        User user = userRepo.findByEmail(request.getEmail());
        if (user == null) {
            err.setStatus(HttpStatus.UNAUTHORIZED);
            err.setMessage("User Not Found");
            return new ResponseEntity<>(err,HttpStatus.UNAUTHORIZED);
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

    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        String email = authentication.getName();
        User user = userRepo.findByEmail(email);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not found");
        }

        CurrentUserDto dto = new CurrentUserDto(
                user.getUserId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                user.getBookings(),
                user.getReviews()
        );

        return ResponseEntity.ok(dto);
    }

    public ResponseEntity<?> updateprofile(Authentication authentication, UpdateProfileDto updateDto) {
        // 1. Validation check for Authentication
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        // 2. Fetch the existing user from the database
        String email = authentication.getName();
        User user = userRepo.findByEmail(email);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not found");
        }

        // 3. Update the fields with the new data
        if (updateDto.getFirstName() != null) {
            user.setFirstName(updateDto.getFirstName());
        }
        if (updateDto.getLastName() != null) {
            user.setLastName(updateDto.getLastName());
        }

        // 4. Save the updated user back to the database
        User updatedUser = userRepo.save(user);

        // 5. Return the updated user info using your existing CurrentUserDto pattern
        CurrentUserDto responseDto = new CurrentUserDto(
                updatedUser.getUserId(),
                updatedUser.getEmail(),
                updatedUser.getFirstName(),
                updatedUser.getLastName(),
                updatedUser.getRole(),
                updatedUser.getBookings(),
                updatedUser.getReviews()
        );

        return ResponseEntity.ok(responseDto);
    }

    public ResponseEntity<?> resendOtp(Authentication authentication, String userEmail) {
        // 1. Validation check for Authentication
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        // 2. Fetch the existing user from the database
        String email = authentication.getName();
        User user = userRepo.findByEmail(email);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not found");
        }

        Optional<OtpVerification> existingOtpOpt = otpRepo.findByEmail(userEmail);

        if (existingOtpOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("No active session found. Please initiate forgot-password first.");
        }

        String newOtp = generateSixDigitOtp();
        OtpVerification otpRecord = existingOtpOpt.get();

        // Update the existing row with the new OTP and reset the 5-minute countdown clock
        otpRecord.refreshOtp(newOtp, 5);
        otpRepo.save(otpRecord);

        // Resend Email
        sendOtpEmail(userEmail, newOtp);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "A new OTP has been sent.");
        response.put("entryId", otpRecord.getId()); // Maintain or return the entry ID

        return ResponseEntity.ok(response);
    }

    public ResponseEntity<?> forgotPassword(String userEmail) {
        if (!userRepo.existsByEmail(userEmail)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User with this email does not exist.");
        }

        String otp = generateSixDigitOtp();

        // OTP verification record expires in 5 minutes
        OtpVerification otpVerification = new OtpVerification(userEmail, otp, LocalDateTime.now().plusMinutes(5));
        OtpVerification savedOtp = otpRepo.save(otpVerification);

        sendOtpEmail(userEmail, otp);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "OTP sent successfully.");
        response.put("entryId", savedOtp.getId());

        return ResponseEntity.ok(response);
    }

    public ResponseEntity<?> verifyOtp(VerifyOtpDto verifyDto) {
        // Query by entry_id (id) and email
        Optional<OtpVerification> otpRecordOpt = otpRepo.findByIdAndEmail(verifyDto.getEntryId(), verifyDto.getEmail());

        VerifyOtpResponse resp = new VerifyOtpResponse();

        if (otpRecordOpt.isEmpty()) {
            resp.setSuccess(false);
            resp.setMessage("Invalid verification details provided.");
            return new ResponseEntity<>(resp,HttpStatus.BAD_REQUEST);
        }

        OtpVerification otpRecord = otpRecordOpt.get();

        // Check if OTP has expired
        if (otpRecord.isExpired()) {
            otpRepo.delete(otpRecord); // Clean up expired record
            resp.setSuccess(false);
            resp.setMessage("OTP has expired. Please request a new one.");
            return new ResponseEntity<>(resp,HttpStatus.GONE);
        }

        // Match the user input OTP against database value
        if (!otpRecord.getOtpValue().equals(verifyDto.getOtpValue())) {
            resp.setSuccess(false);
            resp.setMessage("Incorrect OTP.");
            return new ResponseEntity<>(resp,HttpStatus.BAD_REQUEST);
        }

        // resetToken
        String resetToken = UUID.randomUUID().toString();
        otpRecord.setResetToken(resetToken);
        otpRecord.setOtpValue(null); // Clear OTP so it can't be reused
        otpRepo.save(otpRecord);

        resp.setSuccess(true);
        resp.setMessage("OTP verified successfully. You may now proceed to reset your password.");
        return new ResponseEntity<>(resp,HttpStatus.OK);
    }

    public ResponseEntity<?> resetPassword(ResetPasswordDto resetDto) {
        Optional<OtpVerification> otpRecordOpt = otpRepo.findByResetToken(resetDto.getToken());

        if (otpRecordOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid or expired reset token.");
        }

        OtpVerification otpRecord = otpRecordOpt.get();

        if (otpRecord.isExpired()) {
            otpRepo.delete(otpRecord);
            return ResponseEntity.status(HttpStatus.GONE).body("Reset session expired.");
        }

        // Fetch the actual user
        User user = userRepo.findByEmail(otpRecord.getEmail());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found.");
        }

        String encryptedPassword = bencoder.encode(resetDto.getNewPassword());
        user.setPassword(encryptedPassword);
        userRepo.save(user);

        // Clear OTP
        otpRepo.delete(otpRecord);

        return ResponseEntity.ok("Password updated successfully. You can now log in.");
    }
}
