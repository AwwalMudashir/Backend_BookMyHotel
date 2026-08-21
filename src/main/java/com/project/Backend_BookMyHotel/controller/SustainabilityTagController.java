package com.project.Backend_BookMyHotel.controller;

import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.SustainabilityTagRequest;
import com.project.Backend_BookMyHotel.repository.UserRepository;
import com.project.Backend_BookMyHotel.service.SustainabilityTagService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sustainability-tags")
public class SustainabilityTagController {
    private final SustainabilityTagService tagService;
    private final UserRepository userRepository;

    public SustainabilityTagController(SustainabilityTagService tagService, UserRepository userRepository) {
        this.tagService = tagService;
        this.userRepository = userRepository;
    }

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<?> getForBranch(@PathVariable Long branchId) {
        return tagService.getForBranch(branchId);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','HOTEL_MANAGER')")
    public ResponseEntity<?> getForManagement(@RequestParam(required = false) Long hotelId,
                                               Authentication authentication) {
        return tagService.getForManagement(hotelId, currentUser(authentication));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','HOTEL_MANAGER')")
    public ResponseEntity<?> create(@Valid @RequestBody SustainabilityTagRequest request,
                                    Authentication authentication) {
        return tagService.create(request, currentUser(authentication));
    }

    @PutMapping("/{tagId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','HOTEL_MANAGER')")
    public ResponseEntity<?> update(@PathVariable Long tagId,
                                    @Valid @RequestBody SustainabilityTagRequest request,
                                    Authentication authentication) {
        return tagService.update(tagId, request, currentUser(authentication));
    }

    @DeleteMapping("/{tagId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','HOTEL_MANAGER')")
    public ResponseEntity<?> delete(@PathVariable Long tagId, Authentication authentication) {
        return tagService.deactivate(tagId, currentUser(authentication));
    }

    private User currentUser(Authentication authentication) {
        User actor = userRepository.findByEmail(authentication.getName());
        if (actor == null) {
            throw new org.springframework.security.access.AccessDeniedException("Authenticated user was not found.");
        }
        return actor;
    }
}
