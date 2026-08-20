package com.project.Backend_BookMyHotel.controller;

import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.AddServicesRequest;
import com.project.Backend_BookMyHotel.dto.BookingDetailResponse;
import com.project.Backend_BookMyHotel.dto.BookingResponse;
import com.project.Backend_BookMyHotel.dto.BookingStatus;
import com.project.Backend_BookMyHotel.dto.CreateBookingRequest;
import com.project.Backend_BookMyHotel.repository.UserRepository;
import com.project.Backend_BookMyHotel.service.BookingService;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.file.AccessDeniedException;

@RestController
@RequestMapping({"/booking", "/bookings"})
public class BookingController {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private UserRepository userRepo;

    @PostMapping("/create")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ResponseEntity<?> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            Authentication authentication
    ) {
        return bookingService.createBooking(authentication, request);
    }

    @PostMapping("/{bookingId}/confirm")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ResponseEntity<?> confirmBooking(@PathVariable Long bookingId) {
        return bookingService.confirmBooking(bookingId);
    }

    @PostMapping("/{bookingId}/cancel")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ResponseEntity<?> cancelBooking(
            @PathVariable Long bookingId,
            Authentication authentication
    ) {
        String email = authentication.getName();
        return bookingService.cancelBooking(bookingId, userRepo.findByEmail(email).getId(), userRepo.findByEmail(email).getRole());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public Page<BookingResponse> getUserBookings(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication
    ) throws AccessDeniedException, BadRequestException {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        String email = authentication.getName();
        User user = userRepo.findByEmail(email);

        return bookingService.getUserBookings(user, status, pageable);
    }

    @GetMapping("/{bookingId}")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ResponseEntity<?> getBookingById(
            @PathVariable Long bookingId,
            Authentication authentication
    ) {
        String email = authentication.getName();
        return bookingService.getBookingById(bookingId, userRepo.findByEmail(email).getId(), userRepo.findByEmail(email).getRole());
    }

    @PostMapping("/{bookingId}/services")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ResponseEntity<?> addServices(
            @PathVariable Long bookingId,
            @Valid @RequestBody AddServicesRequest request,
            Authentication authentication
    ) {
        String email = authentication.getName();
        return bookingService.addServicesToBooking(bookingId, userRepo.findByEmail(email).getId(), userRepo.findByEmail(email).getRole(), request);
    }

    @DeleteMapping("/{bookingId}/services/{serviceId}")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ResponseEntity<?> removeService(
            @PathVariable Long bookingId,
            @PathVariable Long serviceId,
            Authentication authentication
    ) {
        String email = authentication.getName();
        return bookingService.removeServiceFromBooking(bookingId, serviceId, userRepo.findByEmail(email).getId(), userRepo.findByEmail(email).getRole());
    }
}
