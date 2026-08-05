package com.project.Backend_BookMyHotel.controller;

import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.UserDto;
import com.project.Backend_BookMyHotel.dto.CategorizedUsersResponse;
import com.project.Backend_BookMyHotel.repository.UserRepository;
import com.project.Backend_BookMyHotel.service.RefreshTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> listUsers() {
        List<User> allUsers = userRepository.findAll();
        
        List<UserDto> admins = new java.util.ArrayList<>();
        List<UserDto> hotelManagers = new java.util.ArrayList<>();
        List<UserDto> customers = new java.util.ArrayList<>();
        
        for (User u : allUsers) {
            if (u.getRole() == null) continue;
            
            UserDto dto = new UserDto(
                u.getId(), 
                u.getFirstName(), 
                u.getLastName(), 
                u.getEmail(), 
                u.getRole().toString(), 
                u.getManagedHotel() != null ? u.getManagedHotel().getId() : null
            );
            
            String role = u.getRole().toString();
            if ("ADMIN".equals(role)) {
                admins.add(dto);
            } else if ("HOTEL_MANAGER".equals(role)) {
                hotelManagers.add(dto);
            } else if ("CUSTOMER".equals(role)) {
                customers.add(dto);
            }
        }
        
        CategorizedUsersResponse response = new CategorizedUsersResponse(admins, hotelManagers, customers);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        // remove refresh tokens
        refreshTokenService.deleteByUserId(id);
        userRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
