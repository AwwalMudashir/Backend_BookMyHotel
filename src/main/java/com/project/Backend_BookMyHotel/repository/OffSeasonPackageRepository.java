package com.project.Backend_BookMyHotel.repository;

import com.project.Backend_BookMyHotel.domain.OffSeasonPackage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OffSeasonPackageRepository extends JpaRepository<OffSeasonPackage, Long> {
    Optional<OffSeasonPackage> findByCodeIgnoreCase(String code);

    List<OffSeasonPackage> findAllByOrderByCreatedAtDesc();

    List<OffSeasonPackage> findByHotelIdOrderByCreatedAtDesc(Long hotelId);

    List<OffSeasonPackage> findByActiveTrueAndBookingEndDateGreaterThanEqualOrderByFeaturedDescStayStartDateAsc(LocalDate today);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM OffSeasonPackage p WHERE p.id = :id")
    Optional<OffSeasonPackage> findByIdForUpdate(@Param("id") Long id);
}
