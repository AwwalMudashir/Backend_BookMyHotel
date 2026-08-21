package com.project.Backend_BookMyHotel.controller;

import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.BookingResponse;
import com.project.Backend_BookMyHotel.repository.UserRepository;
import com.project.Backend_BookMyHotel.service.AnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/manager/analytics")
@PreAuthorize("hasAuthority('HOTEL_MANAGER')")
public class ManagerAnalyticsController {

    private final AnalyticsService analyticsService;
    private final UserRepository userRepository;

    public ManagerAnalyticsController(AnalyticsService analyticsService, UserRepository userRepository) {
        this.analyticsService = analyticsService;
        this.userRepository = userRepository;
    }

    @GetMapping("/summary")
    public ResponseEntity<?> getSummary(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return analyticsService.getHotelAnalytics(managedHotelId(authentication), startDate, endDate);
    }

    @GetMapping("/timeline")
    public ResponseEntity<?> getTimeline(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(analyticsService.getHotelTimeline(
                managedHotelId(authentication), startDate, endDate));
    }

    @GetMapping("/bookings-by-date")
    public ResponseEntity<List<BookingResponse>> getBookingsByDate(
            Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(analyticsService.getBookingsByDate(managedHotelId(authentication), date));
    }

    private Long managedHotelId(Authentication authentication) {
        User actor = userRepository.findByEmail(authentication.getName());
        if (actor == null || actor.getManagedHotel() == null) {
            throw new IllegalStateException("Your manager account is not assigned to a hotel.");
        }
        return actor.getManagedHotel().getId();
    }
}
