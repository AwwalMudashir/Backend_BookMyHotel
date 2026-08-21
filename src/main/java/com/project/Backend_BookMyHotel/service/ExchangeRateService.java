package com.project.Backend_BookMyHotel.service;

import com.project.Backend_BookMyHotel.exception.UnsupportedCurrencyException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
public class ExchangeRateService {
//    Currencies in ISO 4217 code

    private static final String BASE_CURRENCY = "USD";
    private static final int INTERMEDIATE_SCALE = 10;

    private final RestTemplate restTemplate = new RestTemplate();

    // Fetches live exchange rates relative to USD base, cached in Redis for 24 hrs.
    @Cacheable(value = "exchange-rates", key = "'latest'")
    public Map<String, Double> getExchangeRates() {
        String url = "https://api.frankfurter.app/latest?from=USD";
        // Response format: { "rates": { "EUR": 0.92, "GBP": 0.78, "AED": 3.67, "JPY": 155.0 } }
        // Note: the base currency (USD) is never present as a key in "rates".
        ExchangeResponse response = restTemplate.getForObject(url, ExchangeResponse.class);
        return response != null ? response.getRates() : Map.of();
    }

    // Converts an amount from source currency to target currency.
    // Throws UnsupportedCurrencyException rather than silently assuming a 1:1 rate for
    // currencies frankfurter.app (ECB-tracked only) doesn't report.
    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (amount == null) {
            return null;
        }

        String from = fromCurrency.toUpperCase(Locale.ROOT);
        String to = toCurrency.toUpperCase(Locale.ROOT);

        if (from.equals(to)) {
            return amount;
        }

        Map<String, Double> rates = getExchangeRates();

        BigDecimal fromRate = rateFor(rates, from);
        BigDecimal toRate = rateFor(rates, to);

        // Convert to USD standard first, then to target currency, staying in BigDecimal
        // throughout so money never passes through floating-point arithmetic.
        BigDecimal amountInUsd = amount.divide(fromRate, INTERMEDIATE_SCALE, RoundingMode.HALF_UP);
        BigDecimal convertedAmount = amountInUsd.multiply(toRate);

        return convertedAmount.setScale(2, RoundingMode.HALF_UP);
    }

    public Set<String> getSupportedCurrencies() {
        Set<String> currencies = new TreeSet<>(getExchangeRates().keySet());
        currencies.add(BASE_CURRENCY);
        return Set.copyOf(currencies);
    }

    public boolean isSupportedCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return false;
        }
        return getSupportedCurrencies().contains(currency.trim().toUpperCase(Locale.ROOT));
    }

    public String requireSupportedCurrency(String currency) {
        String normalized = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
        if (!isSupportedCurrency(normalized)) {
            throw new UnsupportedCurrencyException(
                    "Currency " + (normalized.isBlank() ? "is required" : normalized)
                            + " is not supported by the current exchange-rate provider. Choose a supported currency.");
        }
        return normalized;
    }

    private BigDecimal rateFor(Map<String, Double> rates, String currency) {
        if (BASE_CURRENCY.equals(currency)) {
            return BigDecimal.ONE;
        }

        Double rate = rates.get(currency);
        if (rate == null) {
            throw new UnsupportedCurrencyException(
                    "Exchange rate not available for currency: " + currency);
        }

        return BigDecimal.valueOf(rate);
    }

    private static class ExchangeResponse {
        private Map<String, Double> rates;
        public Map<String, Double> getRates() { return rates; }
        public void setRates(Map<String, Double> rates) { this.rates = rates; }
    }
}
