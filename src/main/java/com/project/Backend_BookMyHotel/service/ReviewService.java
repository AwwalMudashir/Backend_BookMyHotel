package com.project.Backend_BookMyHotel.service;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.NoSuchElementException;

@Service
public class ReviewService {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private AnalyticsService analyticsService;

    @Transactional
    public ReviewResponse createReview(User user, CreateReviewRequest request) {
        Long branchId = request.branchId();

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new NoSuchElementException("Branch not found with ID: " + branchId));

        boolean eligible = bookingRepository.existsByUserIdAndRoomBranchIdAndStatusAndCheckOutBefore(
                user.getId(), branchId, BookingStatus.CONFIRMED, LocalDate.now());
        if (!eligible) {
            throw new NoEligibleBookingException(
                    "You can only review a branch after a completed stay there.");
        }

        boolean alreadyReviewed = reviewRepository.existsByUserIdAndBranchId(user.getId(), branchId);
        if (alreadyReviewed) {
            throw new DuplicateReviewException("You have already reviewed this branch.");
        }

        Review review = new Review();
        review.setUser(user);
        review.setBranch(branch);
        review.setRating(request.rating());
        review.setComment(request.comment());
        // Automatically verified — the eligibility check above already proved a completed,
        review.setIsVerified(true);

        Review saved = reviewRepository.save(review);

        analyticsService.updateBranchAverageRating(branchId);

        return toReviewResponse(saved);
    }

    @Transactional(readOnly = true)
    public BranchReviewsResponse getReviewsByBranch(Long branchId, Pageable pageable) {
        if (!branchRepository.existsById(branchId)) {
            throw new NoSuchElementException("Branch not found with ID: " + branchId);
        }

        Page<Review> reviewPage = reviewRepository.findByBranchId(branchId, pageable);
        var averageRating = analyticsService.getBranchAverageRating(branchId);

        return BranchReviewsResponse.builder()
                .reviews(reviewPage.getContent().stream().map(this::toReviewResponse).toList())
                .page(reviewPage.getNumber())
                .size(reviewPage.getSize())
                .totalElements(reviewPage.getTotalElements())
                .totalPages(reviewPage.getTotalPages())
                .averageRating(averageRating)
                .build();
    }

    @Transactional
    public void deleteReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new NoSuchElementException("Review not found with ID: " + reviewId));

        Long branchId = review.getBranch().getId();
        reviewRepository.delete(review);

        // Keep the cached average in sync — otherwise a deleted review's rating lingers in it
        // until the fallback TTL expires.
        analyticsService.updateBranchAverageRating(branchId);
    }

    private ReviewResponse toReviewResponse(Review review) {
        String name = review.getUser().getFirstName() + " " + review.getUser().getLastName();
        return ReviewResponse.builder()
                .id(review.getId())
                .branchId(review.getBranch().getId())
                .userId(review.getUser().getId())
                .reviewerName(name)
                .rating(review.getRating())
                .comment(review.getComment())
                .isVerified(review.getIsVerified())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
