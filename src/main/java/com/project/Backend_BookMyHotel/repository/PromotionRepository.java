package com.project.Backend_BookMyHotel.repository;

import com.project.Backend_BookMyHotel.domain.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion,Long> {
    Optional<Promotion> findByCodeIgnoreCase(String code);

    List<Promotion> findByHotelIdAndActiveTrue(Long hotelId);
}
