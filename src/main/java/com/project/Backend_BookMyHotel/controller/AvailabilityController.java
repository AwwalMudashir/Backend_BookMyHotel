package com.project.Backend_BookMyHotel.controller;

import com.project.Backend_BookMyHotel.dto.AvailabilityCalendar;
import com.project.Backend_BookMyHotel.dto.RoomPriceResponse;
import com.project.Backend_BookMyHotel.dto.SetRoomAvailabilityRequest;
import com.project.Backend_BookMyHotel.service.RoomAvailabilityService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/availability")
public class AvailabilityController {

    @Autowired
    private RoomAvailabilityService availabilityService;

    @GetMapping("/{roomId}/calendar")
    public ResponseEntity<AvailabilityCalendar> getRoomCalendar(
            @PathVariable Long roomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        if (startDate == null) {
            startDate = LocalDate.now();
        }
        if (endDate == null) {
            endDate = startDate.plusDays(30);
        }

        AvailabilityCalendar calendar = availabilityService.generateAvailabilityCalendar(roomId, startDate, endDate);
        return ResponseEntity.ok(calendar);
    }

    @GetMapping("/{roomId}/price")
    public ResponseEntity<RoomPriceResponse> getRoomPrice(
            @PathVariable Long roomId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(required = false) String targetCurrency
    ) {
        RoomPriceResponse response = availabilityService.calculateTotalPrice(roomId, checkIn, checkOut, targetCurrency);
        return ResponseEntity.ok(response);
    }
    @PutMapping("/{roomId}/availability")
    @PreAuthorize("hasAuthority('HOTEL_MANAGER')")
    public ResponseEntity<String> setRoomAvailability(
            @PathVariable Long roomId,
            @Valid @RequestBody SetRoomAvailabilityRequest request
    ) {
        availabilityService.setRoomAvailability(roomId, request);
        return ResponseEntity.ok("Room availability and pricing updated successfully.");
    }

    @PutMapping("/{roomId}/update-rate")
    @PreAuthorize("hasAuthority('HOTEL_MANAGER')")
    public ResponseEntity<String> updateDailyRate(
            @PathVariable Long roomId,
            @RequestParam LocalDate date,
            @RequestParam BigDecimal newRate,
            @RequestParam String reason
    ) {
        availabilityService.updateDailyRate(roomId,date,newRate,reason);
        return ResponseEntity.ok("Daily Rate Updated successfully.");
    }
}
