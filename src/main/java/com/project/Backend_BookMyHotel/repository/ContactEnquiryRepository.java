package com.project.Backend_BookMyHotel.repository;

import com.project.Backend_BookMyHotel.domain.ContactEnquiry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContactEnquiryRepository extends JpaRepository<ContactEnquiry, Long> {
}
