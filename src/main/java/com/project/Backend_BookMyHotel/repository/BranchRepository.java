package com.project.Backend_BookMyHotel.repository;

import com.project.Backend_BookMyHotel.domain.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Long> {

    List<Branch> findByHotelId(Long hotelId);

    Optional<Branch> findByCityAndCountry(String city, String country);

    // Fetch branch with rooms eagerly to avoid N+1
    @Query("SELECT b FROM Branch b LEFT JOIN FETCH b.rooms WHERE b.id = :id")
    Optional<Branch> findByIdWithRooms(@Param("id") Long id);

    // Fetch branch with reviews eagerly
    @Query("SELECT b FROM Branch b LEFT JOIN FETCH b.reviews WHERE b.id = :id")
    Optional<Branch> findByIdWithReviews(@Param("id") Long id);

}
