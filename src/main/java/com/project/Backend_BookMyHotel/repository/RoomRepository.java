package com.project.Backend_BookMyHotel.repository;

import com.project.Backend_BookMyHotel.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long>, JpaSpecificationExecutor<Room> {
    List<Room> findByBranchId(Long branchId);

    List<Room> findByBranchIdAndActiveTrue(Long branchId);

    Optional<Room> findByRoomType(String roomType);


    // Find by the new public-facing room identifier (room.roomId)
    Optional<Room> findByRoomId(String roomId);
}
