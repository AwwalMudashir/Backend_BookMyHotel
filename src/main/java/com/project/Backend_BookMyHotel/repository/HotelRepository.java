package com.project.Backend_BookMyHotel.repository;

import com.project.Backend_BookMyHotel.domain.Hotel;
import com.project.Backend_BookMyHotel.dto.HotelSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HotelRepository extends JpaRepository<Hotel,Long> {
    Optional<Hotel> findByName(String name);

    Page<HotelSummary> findBy(Pageable pageable);

    @Query("SELECT h FROM Hotel h JOIN h.branches b WHERE b.id = :branchId")
    Optional<Hotel> findByBranchId(@Param("branchId") Long branchId);
}
