package com.project.Backend_BookMyHotel.controller;

import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.CreateServiceRequest;
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

    @PostMapping
    @PreAuthorize("hasAuthority('HOTEL_MANAGER')")
    public ResponseEntity<?> createService(
            @Valid @RequestBody CreateServiceRequest request,
            Authentication authentication
    ) {
        User manager = userRepo.findByEmail(authentication.getName());
        return serviceService.createService(request, manager);
    }
}
