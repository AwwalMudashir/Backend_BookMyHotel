package com.project.Backend_BookMyHotel.service;

import com.project.Backend_BookMyHotel.domain.Hotel;
import com.project.Backend_BookMyHotel.domain.OtpVerification;
import com.project.Backend_BookMyHotel.domain.RefreshToken;
import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.*;
import com.project.Backend_BookMyHotel.exception.TokenRefreshException;
import com.project.Backend_BookMyHotel.repository.*;
import com.project.Backend_BookMyHotel.util.JwtUtil;
import jakarta.transaction.Transactional;
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
    private HotelRepository hotelRepo;

    @Autowired
    private OtpRepository otpRepo;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepo;

    @Autowired
    private EmailTemplateService emailTemplateService;

    @Autowired
    private ResendEmailService resendEmailService;

    private BCryptPasswordEncoder bencoder = new BCryptPasswordEncoder(12);

    public String generateRandomId(){
        Random random = new Random();
        return "BMH" + (random.nextInt() * 999999);
    }

    private String generateSixDigitOtp() {
        return String.format("%06d", new java.util.Random().nextInt(999999));
    }

    private void sendOtpEmail(String email, String otp) {
        String html = emailTemplateService.otpTemplate(email, otp, 5);

        System.out.println("HTML template built, sending email");
        resendEmailService.sendEmail(
                email,
                "OTP Verification",
                html
        );
        System.out.println("Email Sent successfully");
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
        user.setRole(Role.CUSTOMER);
        user.setManagedHotel(null);
        user.setIsActive('Y');

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
        String html = emailTemplateService.userWelcomeTemplate(user.getFirstName());

        System.out.println("HTML template built, sending email");
        resendEmailService.sendEmail(
                user.getEmail(),
                "Registeration Successful",
                html
        );
        System.out.println("Email Sent successfully");

        return new ResponseEntity<>(user, HttpStatus.CREATED);
    }

    public ResponseEntity<?> addHotelManager(OnboardHotelManager onboardDto) {
        User user = new User();
        ErrorResponse err = new ErrorResponse();

        if (userRepo.existsByEmail(onboardDto.getEmail())){
            err.setStatus(HttpStatus.BAD_REQUEST);
            err.setMessage("User already Exists");
            return new ResponseEntity<>(err, HttpStatus.BAD_REQUEST);
        }

        if (!hotelRepo.existsById(onboardDto.getHotelId())){
            err.setStatus(HttpStatus.BAD_REQUEST);
            err.setMessage("This Hotel Doesn't Exist");
            return new ResponseEntity<>(err, HttpStatus.BAD_REQUEST);
        }

        Hotel hotel = hotelRepo.findById(onboardDto.getHotelId()).get();

        user.setFirstName(onboardDto.getFirstName());
        user.setLastName(onboardDto.getLastName());
        user.setEmail(onboardDto.getEmail());
        user.setPassword(bencoder.encode(onboardDto.getPassword()));
        user.setRole(Role.HOTEL_MANAGER);
        user.setManagedHotel(hotel);
        user.setIsActive('Y');

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
        String html = emailTemplateService.hotelManagerWelcomeTemplate(
                user.getFirstName() + user.getLastName(),
                user.getEmail(),
                onboardDto.getPassword(),
                hotel.getName()
        );

        System.out.println("HTML template built, sending email");
        resendEmailService.sendEmail(
                user.getEmail(),
                "Welcome Hotel Manager to BMH",
                html
        );
        System.out.println("Email Sent successfully");

        return new ResponseEntity<>(user, HttpStatus.CREATED);
    }

    public ResponseEntity<?> createAdmin(AdminDto onboardDto) {
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
        user.setRole(Role.ADMIN);
        user.setManagedHotel(null);
        user.setIsActive('Y');

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
        String html = emailTemplateService.adminWelcomeTemplate(
                user.getFirstName() + user.getLastName(),
                user.getEmail(),
                onboardDto.getPassword()
        );

        System.out.println("HTML template built, sending email");
        resendEmailService.sendEmail(
                user.getEmail(),
                "Welcome Admin to BMH",
                html
        );
        System.out.println("Email Sent successfully");

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
            err.setMessage("User with this credentials doesn't exist");
            return new ResponseEntity<>(err,HttpStatus.UNAUTHORIZED);
        }

        User user = userRepo.findByEmail(request.getEmail());
        if (user == null) {
            err.setStatus(HttpStatus.UNAUTHORIZED);
            err.setMessage("User Not Found");
            return new ResponseEntity<>(err,HttpStatus.UNAUTHORIZED);
        }

        if (user.getIsActive() == 'N') {
            err.setStatus(HttpStatus.UNAUTHORIZED);
            err.setMessage("This Account isn't Active Again");
            return new ResponseEntity<>(err,HttpStatus.UNAUTHORIZED);
        }

        // Generate JWT token
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole());
        RefreshToken refreshToken = refreshTokenService.createOrUpdateRefreshToken(user.getUserId());


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
        String requestRefreshToken = request == null ? null : request.getRefreshToken();

        if (requestRefreshToken == null || requestRefreshToken.isBlank()) {
            throw new TokenRefreshException("Refresh token is required");
        }

        // Rotation looks the token up, checks expiry and issues the replacement in a
        // single transaction, so a stale token can never mint a new access token.
        RefreshToken rotated = refreshTokenService.rotateRefreshToken(requestRefreshToken);

        User user = rotated.getUser();
        String newAccessToken = jwtUtil.generateToken(user.getEmail(), user.getRole());

        return ResponseEntity.ok(new TokenRefreshResponse(newAccessToken, rotated.getToken()));
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

        // Map domain Bookings to lightweight DTOs to avoid recursive serialization of User <-> Booking
        List<BookingDto> bookingDtos = user.getBookings() == null ? List.of() : user.getBookings().stream().map(b -> new BookingDto(
                b.getId(),
                b.getReference(),
                b.getCheckIn(),
                b.getCheckOut(),
                b.getStatus(),
                b.getTotalPrice(),
                b.getEcoPointsEarned(),
                b.getCreatedAt()
        )).toList();

        CurrentUserDto dto = new CurrentUserDto(
                user.getUserId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getGender(),
                user.getRole(),
                user.getManagedHotel(),
                bookingDtos,
                user.getReviews(),
                user.getEcoPoints()
        );

        return ResponseEntity.ok(dto);
    }

    public ResponseEntity<?> getCurrentUserPoints(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        String email = authentication.getName();
        User user = userRepo.findByEmail(email);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not found");
        }

        return ResponseEntity.ok(user.getEcoPoints());
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

        if (updateDto.getGender() != null) {
            user.setGender(updateDto.getGender());
        }

        // 4. Save the updated user back to the database
        User updatedUser = userRepo.save(user);

        // 5. Return the updated user info using your existing CurrentUserDto pattern
        List<BookingDto> bookingDtos = updatedUser.getBookings() == null ? List.of() : updatedUser.getBookings().stream().map(b -> new BookingDto(
                b.getId(),
                b.getReference(),
                b.getCheckIn(),
                b.getCheckOut(),
                b.getStatus(),
                b.getTotalPrice(),
                b.getEcoPointsEarned(),
                b.getCreatedAt()
        )).toList();

        CurrentUserDto responseDto = new CurrentUserDto(
                updatedUser.getUserId(),
                updatedUser.getEmail(),
                updatedUser.getFirstName(),
                updatedUser.getLastName(),
                updatedUser.getGender(),
                updatedUser.getRole(),
                updatedUser.getManagedHotel(),
                bookingDtos,
                updatedUser.getReviews(),
                updatedUser.getEcoPoints()
        );

        return ResponseEntity.ok(responseDto);
    }

    public ResponseEntity<?> resendOtp(String userEmail) {
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

    public ResponseEntity<?> sendOtp(String userEmail) {
        String otp = generateSixDigitOtp();
        OtpVerification otpRecord = new OtpVerification(userEmail, otp, LocalDateTime.now().plusMinutes(5));
        OtpVerification savedOtp = otpRepo.save(otpRecord);

        sendOtpEmail(userEmail, otp);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "A new OTP has been sent.");
        response.put("entryId", otpRecord.getId()); // return the entry ID

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

    public ResponseEntity<?> resetPassword(Authentication authentication , ResetPasswordDto resetDto) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        String email = authentication.getName();
        User user = userRepo.findByEmail(email);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found.");
        }

        String encryptedPassword = bencoder.encode(resetDto.getNewPassword());
        user.setPassword(encryptedPassword);
        userRepo.save(user);

        String html = emailTemplateService.passwordChangedTemplate(user.getFirstName());

        System.out.println("HTML template built, sending email");
        resendEmailService.sendEmail(
                user.getEmail(),
                "Password Updated Successfully !",
                html
        );
        System.out.println("Email Sent successfully");

        return ResponseEntity.ok("Password updated successfully.");
    }

    @Transactional
    public ResponseEntity<?> logout(String refreshToken) {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Refresh token is required");
        }

        refreshTokenService.deleteByToken(refreshToken);

        return ResponseEntity.ok("Success: User has been logged out successfully. Refresh token revoked.");
    }
}
