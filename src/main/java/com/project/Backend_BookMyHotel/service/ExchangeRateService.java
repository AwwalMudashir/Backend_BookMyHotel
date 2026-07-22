package com.project.Backend_BookMyHotel.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Service
public class ExchangeRateService {
//    Currencies in ISO 4217 code

    private final RestTemplate restTemplate = new RestTemplate();

    // Fetches live exchange rates relative to USD base, cached in Redis for 24 hrs.
    @Cacheable(value = "exchange-rates", key = "'latest'")
    public Map<String, Double> getExchangeRates() {
        String url = "https://api.frankfurter.app/latest?from=USD";
        // Response format: { "rates": { "EUR": 0.92, "GBP": 0.78, "AED": 3.67, "JPY": 155.0 } }
        ExchangeResponse response = restTemplate.getForObject(url, ExchangeResponse.class);
        return response != null ? response.getRates() : Map.of();
    }

    // Converts an amount from source currency to target currency.
    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (fromCurrency.equalsIgnoreCase(toCurrency) || amount == null) {
            return amount;
        }

        Map<String, Double> rates = getExchangeRates();

        // Convert to USD standard first, then to target currency
        double fromRate = rates.getOrDefault(fromCurrency.toUpperCase(), 1.0);
        double toRate = rates.getOrDefault(toCurrency.toUpperCase(), 1.0);

        double amountInUsd = amount.doubleValue() / fromRate;
        double convertedAmount = amountInUsd * toRate;

        return BigDecimal.valueOf(convertedAmount).setScale(2, RoundingMode.HALF_UP);
    }

    private static class ExchangeResponse {
        private Map<String, Double> rates;
        public Map<String, Double> getRates() { return rates; }
        public void setRates(Map<String, Double> rates) { this.rates = rates; }
    }
}