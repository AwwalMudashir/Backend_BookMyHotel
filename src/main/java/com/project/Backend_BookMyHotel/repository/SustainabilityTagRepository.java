package com.project.Backend_BookMyHotel.repository;

import com.project.Backend_BookMyHotel.domain.SustainabilityTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SustainabilityTagRepository extends JpaRepository<SustainabilityTag, Long> {
    List<SustainabilityTag> findByHotelIdAndActiveTrueOrderByNameAsc(Long hotelId);
    List<SustainabilityTag> findByActiveTrueOrderByHotelNameAscNameAsc();

    @Query("""
        SELECT tag FROM SustainabilityTag tag
        WHERE tag.active = true
          AND tag.hotel.id = :hotelId
          AND (tag.branch IS NULL OR tag.branch.id = :branchId)
        ORDER BY tag.name ASC
    """)
    List<SustainabilityTag> findAvailableForBranch(
            @Param("hotelId") Long hotelId,
            @Param("branchId") Long branchId);
}
