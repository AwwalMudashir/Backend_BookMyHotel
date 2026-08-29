package com.project.Backend_BookMyHotel.controller;

import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.OffSeasonPackageQuoteRequest;
import com.project.Backend_BookMyHotel.dto.OffSeasonPackageRequest;
import com.project.Backend_BookMyHotel.repository.UserRepository;
import com.project.Backend_BookMyHotel.service.OffSeasonPackageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/packages", "/off-season-packages"})
public class OffSeasonPackageController {
    private final OffSeasonPackageService packageService;
    private final UserRepository userRepository;

    public OffSeasonPackageController(OffSeasonPackageService packageService, UserRepository userRepository) {
        this.packageService = packageService;
        this.userRepository = userRepository;
    }

    @GetMapping("/public/active")
    public ResponseEntity<?> getPublicActive() {
        return ResponseEntity.ok(packageService.getPublicActivePackages());
    }

    @GetMapping("/public/featured")
    public ResponseEntity<?> getPublicFeatured() {
        return ResponseEntity.ok(packageService.getPublicFeaturedPackages());
    }

    @GetMapping("/public/room/{roomId}")
    public ResponseEntity<?> getForRoom(@PathVariable Long roomId) {
        return ResponseEntity.ok(packageService.getPackagesForRoom(roomId));
    }

    @PostMapping("/public/quote")
    public ResponseEntity<?> quote(@Valid @RequestBody OffSeasonPackageQuoteRequest request) {
        return ResponseEntity.ok(packageService.quote(request));
    }

    @GetMapping("/manage")
    @PreAuthorize("hasAnyAuthority('ADMIN','HOTEL_MANAGER')")
    public ResponseEntity<?> getForManagement(@RequestParam(required = false) Long hotelId,
                                              Authentication authentication) {
        return ResponseEntity.ok(packageService.getForManagement(hotelId, currentUser(authentication)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','HOTEL_MANAGER')")
    public ResponseEntity<?> create(@Valid @RequestBody OffSeasonPackageRequest request,
                                    Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(packageService.create(request, currentUser(authentication)));
    }

    @PutMapping("/{packageId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','HOTEL_MANAGER')")
    public ResponseEntity<?> update(@PathVariable Long packageId,
                                    @Valid @RequestBody OffSeasonPackageRequest request,
                                    Authentication authentication) {
        return ResponseEntity.ok(packageService.update(packageId, request, currentUser(authentication)));
    }

    @PatchMapping("/{packageId}/status")
    @PreAuthorize("hasAnyAuthority('ADMIN','HOTEL_MANAGER')")
    public ResponseEntity<?> setStatus(@PathVariable Long packageId,
                                       @RequestParam boolean active,
                                       Authentication authentication) {
        return ResponseEntity.ok(packageService.setActive(packageId, active, currentUser(authentication)));
    }

    @DeleteMapping("/{packageId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','HOTEL_MANAGER')")
    public ResponseEntity<?> deactivate(@PathVariable Long packageId, Authentication authentication) {
        return ResponseEntity.ok(packageService.setActive(packageId, false, currentUser(authentication)));
    }

    private User currentUser(Authentication authentication) {
        User actor = userRepository.findByEmail(authentication.getName());
        if (actor == null) throw new org.springframework.security.access.AccessDeniedException("Authenticated user was not found.");
        return actor;
    }
}
