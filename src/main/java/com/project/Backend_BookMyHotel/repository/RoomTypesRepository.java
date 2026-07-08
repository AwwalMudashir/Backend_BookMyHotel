package com.project.Backend_BookMyHotel.repository;

import com.project.Backend_BookMyHotel.domain.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomTypesRepository extends JpaRepository<RoomType, Long> {
    Optional<List<RoomType>> findAllByCategory(String category);
}
