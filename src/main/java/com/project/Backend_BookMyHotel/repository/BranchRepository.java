package com.project.Backend_BookMyHotel.repository;

import com.project.Backend_BookMyHotel.domain.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Long> {

    List<Branch> findByHotelId(Long hotelId);

    Optional<Branch> findByCityAndCountry(String city, String country);

}
