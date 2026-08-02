package com.project.Backend_BookMyHotel.service_tests;

import com.project.Backend_BookMyHotel.repository.ReviewRepository;
import com.project.Backend_BookMyHotel.service.AnalyticsService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

@ExtendWith(MockitoExtension.class)
public class AnalyticsServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    @Test
    void getBranchAverageRating_RoundsToTwoDecimalPlaces() {
        Mockito.when(reviewRepository.findAverageRatingByBranchId(10L)).thenReturn(4.3333333);

        BigDecimal average = analyticsService.getBranchAverageRating(10L);

        Assertions.assertEquals(0, BigDecimal.valueOf(4.33).compareTo(average));
    }

    @Test
    void getBranchAverageRating_WhenNoReviews_ReturnsZeroNotNull() {
        Mockito.when(reviewRepository.findAverageRatingByBranchId(10L)).thenReturn(null);

        BigDecimal average = analyticsService.getBranchAverageRating(10L);

        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(average));
    }

    @Test
    void updateBranchAverageRating_RecomputesFromRepository() {
        Mockito.when(reviewRepository.findAverageRatingByBranchId(10L)).thenReturn(5.0);

        BigDecimal average = analyticsService.updateBranchAverageRating(10L);

        Assertions.assertEquals(0, BigDecimal.valueOf(5.00).compareTo(average));
    }
}
