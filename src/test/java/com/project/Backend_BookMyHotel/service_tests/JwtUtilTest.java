package com.project.Backend_BookMyHotel.service_tests;

import com.project.Backend_BookMyHotel.dto.Role;
import com.project.Backend_BookMyHotel.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class  JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "0123456789abcdef0123456789abcdef");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 900000L);
    }

    @Test
    void generateTokenShouldExposeUsernameAndRole() {
        String token = jwtUtil.generateToken("awwalmudashir@gmail.com", Role.CUSTOMER);

        assertNotNull(token);
        System.out.println("Extracted Username: " + jwtUtil.extractUsername(token));
        System.out.println("Extracted Role: " + jwtUtil.extractRole(token));

        assertEquals("awwalmudashir@gmail.com", jwtUtil.extractUsername(token));
        assertEquals("CUSTOMER", jwtUtil.extractRole(token));
        assertNotNull(jwtUtil.extractExpiration(token));
    }

    @Test
    void validateTokenShouldSucceedForMatchingUserAndFailForDifferentUser() {
        String token = jwtUtil.generateToken("awwalmudashir@gmail.com", Role.CUSTOMER);
        UserDetails matchingUser = User.withUsername("awwalmudashir@gmail.com")
                .password("password")
                .authorities("ROLE_CUSTOMER")
                .build();
        UserDetails differentUser = User.withUsername("bob@gmail.com")
                .password("password")
                .authorities("ROLE_CUSTOMER")
                .build();

        boolean matchingUserValid = jwtUtil.validateToken(token, matchingUser);
        boolean differentUserValid = jwtUtil.validateToken(token, differentUser);

        System.out.println("[TC-U01 JWT VALIDATION]");
        System.out.println("Expected matching-user result: true | Actual: " + matchingUserValid);
        System.out.println("Expected different-user result: false | Actual: " + differentUserValid);
        System.out.println("Result: token is accepted only for its authenticated owner");

        assertTrue(matchingUserValid);
        assertFalse(differentUserValid);
    }

    @Test
    void validateTokenShouldFailWhenTokenIsExpired() {
        ReflectionTestUtils.setField(jwtUtil, "expiration", 1L);
        String token = jwtUtil.generateToken("awwalmudashir@gmail.com", Role.CUSTOMER);
        assertFalse(jwtUtil.validateToken(token));
        System.out.println("Invalid or Expired JWT Token");
    }
}
