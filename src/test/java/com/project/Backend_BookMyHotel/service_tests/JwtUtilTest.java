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

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "0123456789abcdef0123456789abcdef");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 900000L);
    }

    @Test
    void generateTokenShouldExposeUsernameAndRole() {
        String token = jwtUtil.generateToken("alice", Role.CUSTOMER);

        assertNotNull(token);
        assertEquals("alice", jwtUtil.extractUsername(token));
        assertEquals("CUSTOMER", jwtUtil.extractRole(token));
        assertNotNull(jwtUtil.extractExpiration(token));
    }

    @Test
    void validateTokenShouldSucceedForMatchingUserAndFailForDifferentUser() {
        String token = jwtUtil.generateToken("alice", Role.CUSTOMER);
        UserDetails matchingUser = User.withUsername("alice")
                .password("password")
                .authorities("ROLE_CUSTOMER")
                .build();
        UserDetails differentUser = User.withUsername("bob")
                .password("password")
                .authorities("ROLE_CUSTOMER")
                .build();

        assertTrue(jwtUtil.validateToken(token, matchingUser));
        assertFalse(jwtUtil.validateToken(token, differentUser));
    }

    @Test
    void validateTokenShouldFailWhenTokenIsExpired() {
        ReflectionTestUtils.setField(jwtUtil, "expiration", 1L);
        String token = jwtUtil.generateToken("alice", Role.CUSTOMER);

        assertFalse(jwtUtil.validateToken(token));
    }
}
