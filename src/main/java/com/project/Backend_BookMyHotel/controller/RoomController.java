package com.project.Backend_BookMyHotel.controller;

import com.project.Backend_BookMyHotel.domain.RoomType;
import com.project.Backend_BookMyHotel.dto.RoomRequestDto;
import com.project.Backend_BookMyHotel.service.RoomService;
import com.project.Backend_BookMyHotel.service.HotelManagementAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/room")
public class RoomController {
    @Autowired
    private RoomService roomService;

    @Autowired
    private HotelManagementAccessService accessService;

    @GetMapping("/types")
    public ResponseEntity<?> allRoomTypesByCategory(@RequestParam(required = false) String category){
        return roomService.getAllRoomTypes(category);
    }

    @GetMapping("/categories")
    public ResponseEntity<?> allCategories(){
        List<String> categories = new ArrayList<>(Arrays.asList("Standard", "Deluxe", "Suite", "Presidential Suite"));
        return ResponseEntity.ok(categories);
    }

    @GetMapping("/branches/{branchId}/rooms")
    public ResponseEntity<?> getRoomsByBranch(@PathVariable Long branchId) {
        return roomService.getRoomsByBranch(branchId);
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<?> getRoomById(@PathVariable String roomId) {
        return roomService.getRoomById(roomId);
    }

    @PreAuthorize("hasAnyAuthority('ADMIN','HOTEL_MANAGER')")
    @PostMapping("/branches/{branchId}/create-room")
    public ResponseEntity<?> createRoom(
            @PathVariable Long branchId,
            @RequestPart("room") RoomRequestDto request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            Authentication authentication) {
        accessService.requireBranch(authentication, branchId);
        return roomService.createRoom(branchId, request, images);
    }

    @PreAuthorize("hasAnyAuthority('ADMIN','HOTEL_MANAGER')")
    @PutMapping("/branches/{branchId}/{roomId}")
    public ResponseEntity<?> updateRoom(
            @PathVariable Long branchId,
            @PathVariable String roomId,
            @RequestPart("room") RoomRequestDto request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            Authentication authentication) {
        accessService.requireBranch(authentication, branchId);
        return roomService.updateRoom(branchId, roomId, request, images);
    }

    @PreAuthorize("hasAnyAuthority('ADMIN','HOTEL_MANAGER')")
    @DeleteMapping("/branches/{branchId}/{roomId}")
    public ResponseEntity<?> deleteRoom(@PathVariable Long branchId, @PathVariable String roomId,
                                        Authentication authentication) {
        accessService.requireBranch(authentication, branchId);
        return roomService.deleteRoom(branchId, roomId);
    }

    // Delete a single image from a room's gallery. Provide either publicId (Cloudinary) or url (external image).
    @PreAuthorize("hasAnyAuthority('ADMIN','HOTEL_MANAGER')")
    @DeleteMapping("/branches/{branchId}/{roomId}/images")
    public ResponseEntity<?> deleteRoomImage(
            @PathVariable Long branchId,
            @PathVariable String roomId,
            @RequestParam(required = false) String publicId,
            @RequestParam(required = false) String url,
            Authentication authentication
    ) {
        accessService.requireBranch(authentication, branchId);
        return roomService.deleteRoomImage(branchId, roomId, publicId, url);
    }

}
