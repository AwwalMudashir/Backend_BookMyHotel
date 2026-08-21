package com.project.Backend_BookMyHotel.service;

import com.project.Backend_BookMyHotel.domain.Branch;
import com.project.Backend_BookMyHotel.domain.Room;
import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.repository.BranchRepository;
import com.project.Backend_BookMyHotel.repository.RoomRepository;
import com.project.Backend_BookMyHotel.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
public class HotelManagementAccessService {
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final RoomRepository roomRepository;

    public HotelManagementAccessService(UserRepository userRepository, BranchRepository branchRepository,
                                        RoomRepository roomRepository) {
        this.userRepository = userRepository;
        this.branchRepository = branchRepository;
        this.roomRepository = roomRepository;
    }

    public User currentUser(Authentication authentication) {
        User actor = userRepository.findByEmail(authentication.getName());
        if (actor == null) throw new AccessDeniedException("Authenticated user was not found.");
        return actor;
    }

    public void requireHotel(Authentication authentication, Long hotelId) {
        requireHotel(currentUser(authentication), hotelId);
    }

    public void requireBranch(Authentication authentication, Long branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new NoSuchElementException("Branch not found with ID: " + branchId));
        requireHotel(authentication, branch.getHotel().getId());
    }

    public void requireRoom(Authentication authentication, Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NoSuchElementException("Room not found with ID: " + roomId));
        requireHotel(authentication, room.getBranch().getHotel().getId());
    }

    private void requireHotel(User actor, Long hotelId) {
        if (actor.isAdmin()) return;
        if (actor.getManagedHotel() == null || !actor.getManagedHotel().getId().equals(hotelId)) {
            throw new AccessDeniedException("You can only manage your assigned hotel.");
        }
    }
}
