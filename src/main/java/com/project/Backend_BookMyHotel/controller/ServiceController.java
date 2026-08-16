package com.project.Backend_BookMyHotel.controller;

import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.CreateServiceRequest;
import com.project.Backend_BookMyHotel.dto.UpdateServiceRequest;
import com.project.Backend_BookMyHotel.repository.UserRepository;
import com.project.Backend_BookMyHotel.service.ServiceService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/services")
public class ServiceController {

    @Autowired
    private ServiceService serviceService;

    @Autowired
    private UserRepository userRepo;

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<?> getBranchServices(@PathVariable Long branchId) {
        return serviceService.getServicesByBranch(branchId);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','HOTEL_MANAGER')")
    public ResponseEntity<?> getServices(
            @RequestParam(required = false) Long hotelId,
            Authentication authentication
    ) {
        return serviceService.getServicesForManagement(hotelId, currentUser(authentication));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','HOTEL_MANAGER')")
    public ResponseEntity<?> createService(
            @Valid @RequestBody CreateServiceRequest request,
            Authentication authentication
    ) {
        return serviceService.createService(request, currentUser(authentication));
    }

    @PutMapping("/{serviceId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','HOTEL_MANAGER')")
    public ResponseEntity<?> updateService(
            @PathVariable Long serviceId,
            @Valid @RequestBody UpdateServiceRequest request,
            Authentication authentication
    ) {
        return serviceService.updateService(serviceId, request, currentUser(authentication));
    }

    @DeleteMapping("/{serviceId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','HOTEL_MANAGER')")
    public ResponseEntity<?> deleteService(
            @PathVariable Long serviceId,
            Authentication authentication
    ) {
        return serviceService.deactivateService(serviceId, currentUser(authentication));
    }

    private User currentUser(Authentication authentication) {
        return userRepo.findByEmail(authentication.getName());
    }
}
