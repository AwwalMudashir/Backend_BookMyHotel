package com.project.Backend_BookMyHotel.repository;

import com.project.Backend_BookMyHotel.domain.Service;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

@Repository
public interface ServiceRepository extends JpaRepository<Service, Long> {

    List<Service> findByBranchId(Long branchId);

    List<Service> findByHotelIdAndActiveTrueOrderByNameAsc(Long hotelId);

    List<Service> findByActiveTrueOrderByHotelNameAscNameAsc();

    @Query("""
        SELECT s FROM Service s
        WHERE s.active = true
          AND s.hotel.id = :hotelId
          AND (s.branch IS NULL OR s.branch.id = :branchId)
        ORDER BY s.name ASC
    """)
    List<Service> findAvailableForBranch(
            @Param("hotelId") Long hotelId,
            @Param("branchId") Long branchId
    );
}
