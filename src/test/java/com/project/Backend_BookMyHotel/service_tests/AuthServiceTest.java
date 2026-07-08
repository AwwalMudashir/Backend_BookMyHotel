package com.project.Backend_BookMyHotel.service_tests;

import com.project.Backend_BookMyHotel.domain.OtpVerification;
import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.ErrorResponse;
import com.project.Backend_BookMyHotel.dto.OnboardDto;
import com.project.Backend_BookMyHotel.dto.Role;
import com.project.Backend_BookMyHotel.dto.UpdateProfileDto;
import com.project.Backend_BookMyHotel.repository.OtpRepository;
import com.project.Backend_BookMyHotel.repository.UserRepository;
import com.project.Backend_BookMyHotel.service.UserService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {
    @Mock
    private UserRepository userRepo;

    @Mock
    private OtpRepository otpRepo;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void register_Success() {
        User user = new User();
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setEmail("testemail@gmail.com");
        user.setPassword("Passsword123");
        user.setRole(Role.CUSTOMER);
        Mockito.when(userRepo.existsByEmail(user.getEmail())).thenReturn(false);
        Mockito.when(passwordEncoder.encode(user.getPassword())).thenReturn("hashedPassword");
        Mockito.when(userRepo.save(Mockito.any(User.class))).thenReturn(user);

        OnboardDto obj = new OnboardDto();
        obj.setPassword(user.getPassword());
        obj.setRole(user.getRole());
        obj.setEmail(user.getEmail());
        obj.setFirstName(user.getFirstName());
        obj.setLastName(user.getLastName());

        ResponseEntity<?> response = userService.createUser(obj);

        Assertions.assertNotNull(response);
        Assertions.assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Assertions.assertNotNull(response.getBody());
        Assertions.assertTrue(response.getBody() instanceof User);
        Mockito.verify(userRepo, Mockito.times(1)).save(Mockito.any(User.class));
    }

    @Test
    void register_WhenEmailAlreadyExists_ReturnsBadRequestAndDoesNotSave() {
        OnboardDto obj = new OnboardDto();
        obj.setEmail("existing@example.com");
        obj.setPassword("Password123");
        obj.setFirstName("Jane");
        obj.setLastName("Doe");
        obj.setRole(Role.CUSTOMER);

        Mockito.when(userRepo.existsByEmail(obj.getEmail())).thenReturn(true);

        ResponseEntity<?> response = userService.createUser(obj);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Assertions.assertTrue(response.getBody() instanceof ErrorResponse);
        Mockito.verify(userRepo, Mockito.never()).save(Mockito.any(User.class));
        System.out.println("Test Passed");
    }

    @Test
    void updateProfile_WhenAuthenticatedAndUserExists_ReturnsUpdatedProfile() {
        User existingUser = new User();
        existingUser.setUserId("user-1");
        existingUser.setEmail("user@example.com");
        existingUser.setFirstName("Old");
        existingUser.setLastName("Name");
        existingUser.setRole(Role.CUSTOMER);

        Authentication authentication = Mockito.mock(Authentication.class);
        Mockito.when(authentication.isAuthenticated()).thenReturn(true);
        Mockito.when(authentication.getName()).thenReturn(existingUser.getEmail());
        Mockito.when(userRepo.findByEmail(existingUser.getEmail())).thenReturn(existingUser);
        Mockito.when(userRepo.save(Mockito.any(User.class))).thenReturn(existingUser);

        UpdateProfileDto updateDto = new UpdateProfileDto();
        updateDto.setFirstName("New");
        updateDto.setLastName("User");

        ResponseEntity<?> response = userService.updateprofile(authentication, updateDto);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertTrue(response.getBody() != null);
        Mockito.verify(userRepo).save(Mockito.any(User.class));
    }

    @Test
    void forgotPassword_WhenEmailExists_ReturnsOtpResponse() {
        String email = "forgot@example.com";
        OtpVerification savedOtp = new OtpVerification(email, "123456", java.time.LocalDateTime.now().plusMinutes(5));
        savedOtp.setId(99L);

        Mockito.when(userRepo.existsByEmail(email)).thenReturn(true);
        Mockito.when(otpRepo.save(Mockito.any(OtpVerification.class))).thenReturn(savedOtp);

        ResponseEntity<?> response = userService.forgotPassword(email);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertTrue(response.getBody() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        Assertions.assertEquals("OTP sent successfully.", body.get("message"));
        Assertions.assertEquals(99L, body.get("entryId"));
        Mockito.verify(otpRepo, Mockito.times(1)).save(Mockito.any(OtpVerification.class));
    }
}
