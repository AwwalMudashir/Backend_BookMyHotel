package com.project.Backend_BookMyHotel.service_tests;

import com.project.Backend_BookMyHotel.service.ExchangeRateService;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.Map;

@TestConfiguration
@Profile("test")
public class SearchServiceTestConfig {

    @Bean
    CacheManager testCacheManager() {
        return new ConcurrentMapCacheManager("availability");
    }

    // Real ExchangeRateService calls out to frankfurter.app; tests need deterministic, offline
    // rates instead. Spied (not mocked outright) so #convert()'s real pivot-through-USD math
    // still runs against these fixed rates.
    @Bean
    @Primary
    ExchangeRateService testExchangeRateService() {
        ExchangeRateService service = Mockito.spy(new ExchangeRateService());
        Mockito.doReturn(Map.of("GBP", 0.78, "AED", 3.67, "EUR", 0.92))
                .when(service).getExchangeRates();
        return service;
    }
}
