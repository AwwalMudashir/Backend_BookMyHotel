package com.project.Backend_BookMyHotel.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvailabilityResponseSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Test
    void calendarDayUsesStableIsAvailableJsonProperty() throws Exception {
        AvailabilityCalendar response = AvailabilityCalendar.builder()
                .roomId(12L)
                .days(List.of(AvailabilityCalendar.DailyAvailability.builder()
                        .date(LocalDate.of(2026, 8, 14))
                        .isAvailable(false)
                        .dailyRate(new BigDecimal("120.00"))
                        .currency("GBP")
                        .build()))
                .build();

        JsonNode day = objectMapper.readTree(objectMapper.writeValueAsString(response))
                .path("days")
                .path(0);

        assertTrue(day.has("isAvailable"));
        assertFalse(day.path("isAvailable").asBoolean());
        assertFalse(day.has("available"));
    }
}
