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

    // Fetch branch with services eagerly
    @Query("SELECT b FROM Branch b LEFT JOIN FETCH b.services WHERE b.id = :id")
    Optional<Branch> findByIdWithServices(@Param("id") Long id);

    // Every distinct currency in play for a search's branches, so a currency-aware price filter
    // knows which currencies to convert minPrice/maxPrice into. city/country are optional filters,
    // matching how the search specification itself narrows branches.
    @Query("SELECT DISTINCT b.currency FROM Branch b " +
            "WHERE (:city IS NULL OR LOWER(b.city) = LOWER(:city)) " +
            "AND (:country IS NULL OR LOWER(b.country) = LOWER(:country))")
    List<String> findDistinctCurrencies(@Param("city") String city, @Param("country") String country);

}
