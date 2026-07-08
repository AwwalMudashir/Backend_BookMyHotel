package com.project.Backend_BookMyHotel.repository;

import com.project.Backend_BookMyHotel.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {
    Optional<Room> findByBranchId(Long branchId);

    Optional<Room> findByRoomType(String roomType);
}
