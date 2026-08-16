package com.project.Backend_BookMyHotel.controller;

import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.ApplyPromotionRequest;
import com.project.Backend_BookMyHotel.dto.CreatePromotionRequest;
import com.project.Backend_BookMyHotel.dto.PromotionBreakdownResponse;
import com.project.Backend_BookMyHotel.dto.UpdatePromotionRequest;
import com.project.Backend_BookMyHotel.repository.UserRepository;
import com.project.Backend_BookMyHotel.service.PromotionService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/promotion", "/promotions"})
public class PromotionController {

    @Autowired
    private PromotionService promotionService;

    @Autowired
    private UserRepository userRepo;

    @PostMapping("/apply")
    public PromotionBreakdownResponse applyPromotion(
            @Valid @RequestBody ApplyPromotionRequest request
    ) {
        return promotionService.applyPromotion(
                request.getCode(),
                request.getTotalPrice(),
                request.getHotelId()
        );
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','HOTEL_MANAGER')")
    public ResponseEntity<?> createPromotion(
            @Valid @RequestBody CreatePromotionRequest request,
            Authentication authentication
    ) {
        User actor = userRepo.findByEmail(authentication.getName());
        return promotionService.createPromotion(request, actor);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','HOTEL_MANAGER')")
    public ResponseEntity<?> updatePromotion(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePromotionRequest request,
            Authentication authentication
    ) {
        User actor = userRepo.findByEmail(authentication.getName());
        return promotionService.updatePromotion(id, request, actor);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','HOTEL_MANAGER')")
    public ResponseEntity<?> deactivatePromotion(
            @PathVariable Long id,
            Authentication authentication
    ) {
        User actor = userRepo.findByEmail(authentication.getName());
        return promotionService.deactivatePromotion(id, actor);
    }

    @GetMapping
    public ResponseEntity<?> listActivePromotions(@RequestParam Long hotelId) {
        return promotionService.listActivePromotions(hotelId);
    }

    @GetMapping("/active")
    public ResponseEntity<?> listGlobalActivePromotions() {
        return promotionService.listGlobalActivePromotions();
    }
}
