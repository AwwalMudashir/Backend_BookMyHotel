package com.project.Backend_BookMyHotel.repository;

import com.project.Backend_BookMyHotel.domain.Service;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceRepository extends JpaRepository<Service, Long> {

    List<Service> findByBranchId(Long branchId);
}
