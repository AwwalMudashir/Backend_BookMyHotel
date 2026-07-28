package com.project.Backend_BookMyHotel.controller;

import com.project.Backend_BookMyHotel.dto.ApplyPromotionRequest;
import com.project.Backend_BookMyHotel.dto.PromotionBreakdownResponse;
import com.project.Backend_BookMyHotel.service.PromotionService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/promotion")
public class PromotionController {

    @Autowired
    private PromotionService promotionService;

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
}
