package com.project.Backend_BookMyHotel.service;

import com.project.Backend_BookMyHotel.domain.RoomType;
import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.repository.RoomTypesRepository;
import com.project.Backend_BookMyHotel.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RoomService {

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private RoomTypesRepository roomTypesRepo;

    public ResponseEntity<?> getAllRoomTypes(Authentication authentication, String category) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        // 2. Fetch the existing user from the database
        String email = authentication.getName();
        User user = userRepo.findByEmail(email);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not found");
        }

        if (category == "" || category.isBlank() || category == null){
            List<RoomType> roomTypes = roomTypesRepo.findAll();
            return ResponseEntity.ok(roomTypes);
        }

        List<RoomType> roomTypes = roomTypesRepo.findAllByCategory(category).get();
        return ResponseEntity.ok(roomTypes);
    }
}
