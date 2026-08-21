package com.project.Backend_BookMyHotel.controller;

import com.project.Backend_BookMyHotel.service.ExchangeRateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/exchange-rates")
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    public ExchangeRateController(ExchangeRateService exchangeRateService) {
        this.exchangeRateService = exchangeRateService;
    }

    @GetMapping("/supported-currencies")
    public ResponseEntity<?> getSupportedCurrencies() {
        return ResponseEntity.ok(Map.of("currencies", exchangeRateService.getSupportedCurrencies()));
    }
}
