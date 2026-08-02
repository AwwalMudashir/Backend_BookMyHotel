package com.project.Backend_BookMyHotel.controller;

import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.BookingResponse;
import com.project.Backend_BookMyHotel.dto.BookingStatus;
import com.project.Backend_BookMyHotel.dto.UpdateReservationStatusRequest;
import com.project.Backend_BookMyHotel.repository.UserRepository;
import com.project.Backend_BookMyHotel.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/admin/reservations")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminReservationController {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private UserRepository userRepo;

    @GetMapping
    public Page<BookingResponse> listReservations(
            @RequestParam(required = false) Long hotelId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("checkIn").descending());
        return bookingService.getReservationsForAdmin(hotelId, date, status, pageable);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateReservationStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateReservationStatusRequest request,
            Authentication authentication
    ) {
        User admin = userRepo.findByEmail(authentication.getName());
        return bookingService.updateReservationStatus(id, request.status(), admin.getId());
    }
}
