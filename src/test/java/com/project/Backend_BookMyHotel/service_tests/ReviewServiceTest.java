package com.project.Backend_BookMyHotel.service_tests;

import com.project.Backend_BookMyHotel.domain.Branch;
import com.project.Backend_BookMyHotel.domain.Review;
import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.BookingStatus;
import com.project.Backend_BookMyHotel.dto.BranchReviewsResponse;
import com.project.Backend_BookMyHotel.dto.CreateReviewRequest;
import com.project.Backend_BookMyHotel.dto.ReviewResponse;
import com.project.Backend_BookMyHotel.exception.DuplicateReviewException;
import com.project.Backend_BookMyHotel.exception.NoEligibleBookingException;
import com.project.Backend_BookMyHotel.repository.BookingRepository;
import com.project.Backend_BookMyHotel.repository.BranchRepository;
import com.project.Backend_BookMyHotel.repository.ReviewRepository;
import com.project.Backend_BookMyHotel.service.AnalyticsService;
import com.project.Backend_BookMyHotel.service.ReviewService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private AnalyticsService analyticsService;

    @InjectMocks
    private ReviewService reviewService;

    private User customer;
    private Branch branch;

    @BeforeEach
    void setUp() {
        customer = new User();
        customer.setId(1L);
        customer.setFirstName("Jane");
        customer.setLastName("Doe");

        branch = new Branch();
        branch.setId(10L);
    }

    @Test
    void createReview_WhenEligible_SavesReviewAndRefreshesAverage() {
        Mockito.when(branchRepository.findById(10L)).thenReturn(Optional.of(branch));
        Mockito.when(bookingRepository.existsByUserIdAndRoomBranchIdAndStatusAndCheckOutBefore(
                        1L, 10L, BookingStatus.CONFIRMED, LocalDate.now()))
                .thenReturn(true);
        Mockito.when(reviewRepository.existsByUserIdAndBranchId(1L, 10L)).thenReturn(false);
        Mockito.when(reviewRepository.save(Mockito.any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setId(500L);
            return r;
        });

        CreateReviewRequest request = new CreateReviewRequest(10L, 5, "Fantastic stay");

        ReviewResponse response = reviewService.createReview(customer, request);

        Assertions.assertEquals(500L, response.getId());
        Assertions.assertEquals(10L, response.getBranchId());
        Assertions.assertEquals(5, response.getRating());
        Assertions.assertEquals("Fantastic stay", response.getComment());
        Assertions.assertTrue(response.getIsVerified());
        Assertions.assertEquals("Jane Doe", response.getReviewerName());

        Mockito.verify(analyticsService).updateBranchAverageRating(10L);
    }

    @Test
    void createReview_WhenNoCompletedBookingAtBranch_ThrowsNoEligibleBookingException() {
        Mockito.when(branchRepository.findById(10L)).thenReturn(Optional.of(branch));
        Mockito.when(bookingRepository.existsByUserIdAndRoomBranchIdAndStatusAndCheckOutBefore(
                        1L, 10L, BookingStatus.CONFIRMED, LocalDate.now()))
                .thenReturn(false);

        CreateReviewRequest request = new CreateReviewRequest(10L, 5, "Fantastic stay");

        Assertions.assertThrows(NoEligibleBookingException.class,
                () -> reviewService.createReview(customer, request));
        Mockito.verify(reviewRepository, Mockito.never()).save(Mockito.any(Review.class));
    }

    @Test
    void createReview_WhenAlreadyReviewed_ThrowsDuplicateReviewException() {
        Mockito.when(branchRepository.findById(10L)).thenReturn(Optional.of(branch));
        Mockito.when(bookingRepository.existsByUserIdAndRoomBranchIdAndStatusAndCheckOutBefore(
                        1L, 10L, BookingStatus.CONFIRMED, LocalDate.now()))
                .thenReturn(true);
        Mockito.when(reviewRepository.existsByUserIdAndBranchId(1L, 10L)).thenReturn(true);

        CreateReviewRequest request = new CreateReviewRequest(10L, 5, "Fantastic stay");

        Assertions.assertThrows(DuplicateReviewException.class,
                () -> reviewService.createReview(customer, request));
        Mockito.verify(reviewRepository, Mockito.never()).save(Mockito.any(Review.class));
    }

    @Test
    void createReview_WhenBranchNotFound_ThrowsNoSuchElementException() {
        Mockito.when(branchRepository.findById(999L)).thenReturn(Optional.empty());

        CreateReviewRequest request = new CreateReviewRequest(999L, 5, "Fantastic stay");

        Assertions.assertThrows(NoSuchElementException.class,
                () -> reviewService.createReview(customer, request));
    }

    @Test
    void getReviewsByBranch_ReturnsPaginatedReviewsWithAverageRating() {
        Review review = new Review();
        review.setId(500L);
        review.setUser(customer);
        review.setBranch(branch);
        review.setRating(4);
        review.setComment("Great location");
        review.setIsVerified(true);

        Page<Review> page = new PageImpl<>(List.of(review), PageRequest.of(0, 10), 1);

        Mockito.when(branchRepository.existsById(10L)).thenReturn(true);
        Mockito.when(reviewRepository.findByBranchId(10L, PageRequest.of(0, 10))).thenReturn(page);
        Mockito.when(analyticsService.getBranchAverageRating(10L)).thenReturn(BigDecimal.valueOf(4.00));

        BranchReviewsResponse response = reviewService.getReviewsByBranch(10L, PageRequest.of(0, 10));

        Assertions.assertEquals(1, response.getReviews().size());
        Assertions.assertEquals(1, response.getTotalElements());
        Assertions.assertEquals(0, BigDecimal.valueOf(4.00).compareTo(response.getAverageRating()));
    }

    @Test
    void getReviewsByBranch_WhenBranchNotFound_ThrowsNoSuchElementException() {
        Mockito.when(branchRepository.existsById(999L)).thenReturn(false);

        Assertions.assertThrows(NoSuchElementException.class,
                () -> reviewService.getReviewsByBranch(999L, PageRequest.of(0, 10)));
    }

    @Test
    void deleteReview_RemovesReviewAndRefreshesAverage() {
        Review review = new Review();
        review.setId(500L);
        review.setBranch(branch);
        Mockito.when(reviewRepository.findById(500L)).thenReturn(Optional.of(review));

        reviewService.deleteReview(500L);

        Mockito.verify(reviewRepository).delete(review);
        Mockito.verify(analyticsService).updateBranchAverageRating(10L);
    }

    @Test
    void deleteReview_WhenNotFound_ThrowsNoSuchElementException() {
        Mockito.when(reviewRepository.findById(999L)).thenReturn(Optional.empty());

        Assertions.assertThrows(NoSuchElementException.class,
                () -> reviewService.deleteReview(999L));
        Mockito.verify(analyticsService, Mockito.never()).updateBranchAverageRating(Mockito.anyLong());
    }
}