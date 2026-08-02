package com.project.Backend_BookMyHotel.repository;

import com.project.Backend_BookMyHotel.domain.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByUserIdAndBranchId(Long userId, Long branchId);

    Page<Review> findByBranchId(Long branchId, Pageable pageable);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.branch.id = :branchId")
    Double findAverageRatingByBranchId(@Param("branchId") Long branchId);
}
