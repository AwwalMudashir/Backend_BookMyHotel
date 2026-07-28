package com.project.Backend_BookMyHotel.service_tests;

import com.project.Backend_BookMyHotel.exception.UnsupportedCurrencyException;
import com.project.Backend_BookMyHotel.service.ExchangeRateService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Map;

public class ExchangeRateServiceTest {

    private ExchangeRateService serviceWithRates(Map<String, Double> rates) {
        ExchangeRateService service = Mockito.spy(new ExchangeRateService());
        Mockito.doReturn(rates).when(service).getExchangeRates();
        return service;
    }

    @Test
    void convert_SameCurrency_ReturnsAmountUnchanged() {
        ExchangeRateService service = serviceWithRates(Map.of("GBP", 0.78));

        BigDecimal result = service.convert(BigDecimal.valueOf(100), "gbp", "GBP");

        Assertions.assertEquals(BigDecimal.valueOf(100), result);
    }

    @Test
    void convert_BetweenTwoNonUsdCurrencies_PivotsThroughUsd() {
        ExchangeRateService service = serviceWithRates(Map.of("GBP", 0.78, "AED", 3.67));

        // 78 GBP -> 100 USD -> 367 AED
        BigDecimal result = service.convert(BigDecimal.valueOf(78), "GBP", "AED");

        Assertions.assertEquals(0, BigDecimal.valueOf(367.00).compareTo(result));
    }

    @Test
    void convert_ToUsd_TreatsUsdAsBaseRateOfOne() {
        // frankfurter's "rates" map never contains the base currency (USD) itself
        ExchangeRateService service = serviceWithRates(Map.of("GBP", 0.5));

        BigDecimal result = service.convert(BigDecimal.valueOf(50), "GBP", "USD");

        Assertions.assertEquals(0, BigDecimal.valueOf(100.00).compareTo(result));
    }

    @Test
    void convert_UnsupportedCurrency_ThrowsInsteadOfDefaultingToOne() {
        ExchangeRateService service = serviceWithRates(Map.of("GBP", 0.78));

        Assertions.assertThrows(UnsupportedCurrencyException.class,
                () -> service.convert(BigDecimal.valueOf(100), "GBP", "ZWL"));
    }
}
