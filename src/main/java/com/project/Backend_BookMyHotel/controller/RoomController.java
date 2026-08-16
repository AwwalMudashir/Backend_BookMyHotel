package com.project.Backend_BookMyHotel.controller;

import com.project.Backend_BookMyHotel.domain.RoomType;
import com.project.Backend_BookMyHotel.dto.RoomRequestDto;
import com.project.Backend_BookMyHotel.service.RoomService;
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

    @GetMapping("/types")
    public ResponseEntity<?> allRoomTypesByCategory(Authentication authentication, @RequestParam String category){
        return roomService.getAllRoomTypes(authentication,category);
    }

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

    @PreAuthorize("hasAuthority('ADMIN')")
    @PostMapping("/branches/{branchId}/create-room")
    public ResponseEntity<?> createRoom(
            @PathVariable Long branchId,
            @RequestPart("room") RoomRequestDto request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        return roomService.createRoom(branchId, request, images);
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @PutMapping("/branches/{branchId}/{roomId}")
    public ResponseEntity<?> updateRoom(
            @PathVariable Long branchId,
            @PathVariable String roomId,
            @RequestPart("room") RoomRequestDto request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        return roomService.updateRoom(branchId, roomId, request, images);
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @DeleteMapping("/branches/{branchId}/{roomId}")
    public ResponseEntity<?> deleteRoom(@PathVariable Long branchId, @PathVariable String roomId) {
        return roomService.deleteRoom(branchId, roomId);
    }

    // Delete a single image from a room's gallery. Provide either publicId (Cloudinary) or url (external image).
    @PreAuthorize("hasAuthority('ADMIN')")
    @DeleteMapping("/branches/{branchId}/{roomId}/images")
    public ResponseEntity<?> deleteRoomImage(
            @PathVariable Long branchId,
            @PathVariable String roomId,
            @RequestParam(required = false) String publicId,
            @RequestParam(required = false) String url
    ) {
        return roomService.deleteRoomImage(branchId, roomId, publicId, url);
    }

}