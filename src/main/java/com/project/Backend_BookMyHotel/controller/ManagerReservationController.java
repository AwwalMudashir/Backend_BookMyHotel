package com.project.Backend_BookMyHotel.controller;

import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.BookingResponse;
import com.project.Backend_BookMyHotel.dto.BookingStatus;
import com.project.Backend_BookMyHotel.dto.UpdateReservationStatusRequest;
import com.project.Backend_BookMyHotel.repository.UserRepository;
import com.project.Backend_BookMyHotel.service.BookingService;
import jakarta.validation.Valid;
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
@RequestMapping("/manager/reservations")
@PreAuthorize("hasAuthority('HOTEL_MANAGER')")
public class ManagerReservationController {
    private final BookingService bookingService;
    private final UserRepository userRepository;

    public ManagerReservationController(BookingService bookingService, UserRepository userRepository) {
        this.bookingService = bookingService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public Page<BookingResponse> list(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        User manager = manager(authentication);
        return bookingService.getReservationsForAdmin(manager.getManagedHotel().getId(), date, status,
                PageRequest.of(page, size, Sort.by("checkIn").descending()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @Valid @RequestBody UpdateReservationStatusRequest request,
                                    Authentication authentication) {
        return bookingService.updateReservationStatusForManager(id, request.status(), manager(authentication));
    }

    private User manager(Authentication authentication) {
        User manager = userRepository.findByEmail(authentication.getName());
        if (manager == null || manager.getManagedHotel() == null) {
            throw new IllegalStateException("Your manager account is not assigned to a hotel.");
        }
        return manager;
    }
}
