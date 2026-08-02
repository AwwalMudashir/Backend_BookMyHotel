package com.project.Backend_BookMyHotel.service_tests;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.project.Backend_BookMyHotel.domain.RefreshToken;
import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.GoogleLoginRequest;
import com.project.Backend_BookMyHotel.dto.JwtResponse;
import com.project.Backend_BookMyHotel.dto.Role;
import com.project.Backend_BookMyHotel.exception.InvalidGoogleTokenException;
import com.project.Backend_BookMyHotel.repository.UserRepository;
import com.project.Backend_BookMyHotel.service.EmailTemplateService;
import com.project.Backend_BookMyHotel.service.GoogleAuthService;
import com.project.Backend_BookMyHotel.service.RefreshTokenService;
import com.project.Backend_BookMyHotel.service.ResendEmailService;
import com.project.Backend_BookMyHotel.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GoogleAuthServiceTest {

    @Mock
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    @Mock
    private UserRepository userRepo;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private EmailTemplateService emailTemplateService;

    @Mock
    private ResendEmailService resendEmailService;

    @Mock
    private GoogleIdToken googleIdToken;

    @InjectMocks
    private GoogleAuthService googleAuthService;

    private GoogleIdToken.Payload payload;

    @BeforeEach
    void setUp() {
        payload = new GoogleIdToken.Payload();
        payload.setEmail("newuser@gmail.com");
        payload.setEmailVerified(true);
        payload.setSubject("google-sub-123");
        payload.set("given_name", "Ada");
        payload.set("family_name", "Lovelace");
    }

    @Test
    void newGoogleAccountIsCreatedAndReturnsAppJwt() throws Exception {
        when(googleIdTokenVerifier.verify("valid-token")).thenReturn(googleIdToken);
        when(googleIdToken.getPayload()).thenReturn(payload);
        when(userRepo.findByGoogleId("google-sub-123")).thenReturn(Optional.empty());
        when(userRepo.findByEmail("newuser@gmail.com")).thenReturn(null);
        when(userRepo.existsByUserId(anyString())).thenReturn(false);
        when(jwtUtil.generateToken(eq("newuser@gmail.com"), eq(Role.CUSTOMER))).thenReturn("app-jwt");

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("app-refresh");
        when(refreshTokenService.createOrUpdateRefreshToken(anyString())).thenReturn(refreshToken);
        when(emailTemplateService.userWelcomeTemplate(anyString())).thenReturn("<html></html>");

        ResponseEntity<?> response = googleAuthService.googleLogin(new GoogleLoginRequest("valid-token"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JwtResponse body = (JwtResponse) response.getBody();
        assertNotNull(body);
        assertEquals("app-jwt", body.getToken());
        assertEquals("app-refresh", body.getRefreshToken());
        assertEquals("newuser@gmail.com", body.getEmail());
        assertEquals("Ada", body.getFirstName());
        assertEquals(Role.CUSTOMER, body.getRole());

        verify(userRepo).save(argThat(u ->
                "newuser@gmail.com".equals(u.getEmail())
                        && "google-sub-123".equals(u.getGoogleId())
                        && u.getRole() == Role.CUSTOMER
                        && u.getPassword() != null));
        verify(resendEmailService).sendEmail(eq("newuser@gmail.com"), anyString(), anyString());
    }

    @Test
    void existingPasswordAccountIsLinkedByEmailInsteadOfDuplicated() throws Exception {
        User existing = new User();
        existing.setUserId("BMH1");
        existing.setEmail("newuser@gmail.com");
        existing.setFirstName("Ada");
        existing.setLastName("Lovelace");
        existing.setRole(Role.CUSTOMER);
        existing.setIsActive('Y');
        existing.setPassword("$2a$12$existingHash");

        when(googleIdTokenVerifier.verify("valid-token")).thenReturn(googleIdToken);
        when(googleIdToken.getPayload()).thenReturn(payload);
        when(userRepo.findByGoogleId("google-sub-123")).thenReturn(Optional.empty());
        when(userRepo.findByEmail("newuser@gmail.com")).thenReturn(existing);
        when(jwtUtil.generateToken(eq("newuser@gmail.com"), eq(Role.CUSTOMER))).thenReturn("app-jwt");

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("app-refresh");
        when(refreshTokenService.createOrUpdateRefreshToken("BMH1")).thenReturn(refreshToken);

        ResponseEntity<?> response = googleAuthService.googleLogin(new GoogleLoginRequest("valid-token"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("google-sub-123", existing.getGoogleId());
        verify(userRepo).save(existing);
        verify(userRepo, never()).existsByUserId(anyString());
        verify(resendEmailService, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    void invalidTokenIsRejected() throws Exception {
        when(googleIdTokenVerifier.verify("bad-token")).thenReturn(null);

        assertThrows(InvalidGoogleTokenException.class,
                () -> googleAuthService.googleLogin(new GoogleLoginRequest("bad-token")));
    }

    @Test
    void unverifiedEmailIsRejected() throws Exception {
        payload.setEmailVerified(false);
        when(googleIdTokenVerifier.verify("valid-token")).thenReturn(googleIdToken);
        when(googleIdToken.getPayload()).thenReturn(payload);

        assertThrows(InvalidGoogleTokenException.class,
                () -> googleAuthService.googleLogin(new GoogleLoginRequest("valid-token")));
    }
}
