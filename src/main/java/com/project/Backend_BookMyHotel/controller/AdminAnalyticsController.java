package com.project.Backend_BookMyHotel.controller;

import com.project.Backend_BookMyHotel.dto.BookingResponse;
import com.project.Backend_BookMyHotel.service.AnalyticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/admin/analytics")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminAnalyticsController {

    @Autowired
    private AnalyticsService analyticsService;

    // startDate/endDate default to a trailing 30-day window when omitted — see
    // AnalyticsService.resolveDateRange.
    @GetMapping("/summary")
    public ResponseEntity<?> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return analyticsService.getOverallSummary(startDate, endDate);
    }

    @GetMapping("/hotel/{id}")
    public ResponseEntity<?> getHotelAnalytics(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return analyticsService.getHotelAnalytics(id, startDate, endDate);
    }

    @GetMapping("/bookings-by-date")
    public ResponseEntity<List<BookingResponse>> getBookingsByDate(
            @RequestParam Long hotelId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(analyticsService.getBookingsByDate(hotelId, date));
    }
}
