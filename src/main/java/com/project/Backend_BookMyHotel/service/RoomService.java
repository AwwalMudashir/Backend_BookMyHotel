package com.project.Backend_BookMyHotel.service;

import com.project.Backend_BookMyHotel.domain.Branch;
import com.project.Backend_BookMyHotel.domain.Room;
import com.project.Backend_BookMyHotel.domain.RoomType;
import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.RoomRequestDto;
import com.project.Backend_BookMyHotel.dto.RoomResponseDto;
import com.project.Backend_BookMyHotel.repository.BranchRepository;
import com.project.Backend_BookMyHotel.repository.RoomRepository;
import com.project.Backend_BookMyHotel.repository.RoomTypesRepository;
import com.project.Backend_BookMyHotel.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class RoomService {

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private RoomTypesRepository roomTypesRepo;

    @Autowired
    private BranchRepository branchRepo;

    @Autowired
    private RoomRepository roomRepo;

    @Autowired
    private CloudinaryService cloudinaryService;

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

    public ResponseEntity<?> getRoomsByBranch(Long branchId) {
        if (!branchRepo.existsById(branchId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Error: Branch with ID " + branchId + " does not exist.");
        }

        List<Room> rooms = roomRepo.findByBranchId(branchId);
        List<RoomResponseDto> dtoList = rooms.stream().map(this::mapToRoomResponseDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtoList);
    }

    public ResponseEntity<?> getRoomById(String roomIdOrPublic) {
        Optional<Room> roomOpt = findRoomByIdentifier(roomIdOrPublic);
        if (roomOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Error: Room with identifier " + roomIdOrPublic + " not found.");
        }
        return ResponseEntity.ok(mapToRoomResponseDto(roomOpt.get()));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createRoom(Long branchId, RoomRequestDto request, List<MultipartFile> imageFiles) {
        Optional<Branch> branchOpt = branchRepo.findById(branchId);

        if (branchOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Error: Branch with ID " + branchId + " not found.");
        }

        Optional<RoomType> typeOpt = roomTypesRepo.findById(request.getRoomTypeId());
        if (typeOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error: Assigned RoomType ID " + request.getRoomTypeId() + " does not exist.");
        }

        Room room = new Room();
        room.setBranch(branchOpt.get());
        room.setRoomType(typeOpt.get().getName());
        room.setPricePerNight(request.getPricePerNight());
        room.setAmenities(request.getAmenities());
        if (request.getTags() != null) {
            room.setTags(request.getTags());
        }

        // Process images with Cloudinary
        List<String> imageUrls = new ArrayList<>();
        List<String> publicIds = new ArrayList<>();
        if (imageFiles != null && !imageFiles.isEmpty()) {
            for (MultipartFile file : imageFiles) {
                if (file != null && !file.isEmpty()) {
                    try {
                        Map<String, String> uploadResult = cloudinaryService.uploadImage(file);
                        imageUrls.add(uploadResult.get("url"));
                        publicIds.add(uploadResult.get("publicId"));
                    } catch (Exception e) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body("Error uploading file [" + file.getOriginalFilename() + "]: " + e.getMessage());
                    }
                }
            }
        }
        room.setImages(imageUrls);

        Room savedRoom = roomRepo.save(room);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapToRoomResponseDto(savedRoom));
    }

    @Transactional
    public ResponseEntity<?> updateRoom(Long branchId, String roomIdOrPublic, RoomRequestDto request, List<MultipartFile> imageFiles) {
        if (!branchRepo.existsById(branchId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Error: Target Branch not found.");
        }

        Optional<Room> roomOpt = findRoomByIdentifier(roomIdOrPublic);
        if (roomOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Error: Room not found for identifier: " + roomIdOrPublic);
        }

        Room room = roomOpt.get();

        // Security Check: Enforce branch ownership integrity
        if (!room.getBranch().getId().equals(branchId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Error: Security Violation. Room identifier " + roomIdOrPublic + " does not belong to Branch ID " + branchId + ".");
        }

        if (request.getRoomTypeId() != null) {
            Optional<RoomType> typeOpt = roomTypesRepo.findById(request.getRoomTypeId());
            if (typeOpt.isEmpty()) return ResponseEntity.badRequest().body("Error: Invalid RoomType ID.");
            room.setRoomType(typeOpt.get().getName());
        }

        room.setPricePerNight(request.getPricePerNight());
        room.setAmenities(request.getAmenities());
        if (request.getTags() != null) {
            room.setTags(request.getTags());
        }

        // Process and append new images to the existing room gallery
        if (imageFiles != null && !imageFiles.isEmpty()) {
            List<String> currentImages = room.getImages();
            List<String> currentPublicIds = room.getPublicIds();

            if (currentImages == null) {
                currentImages = new ArrayList<>();
            }

            if (currentPublicIds == null) {
                currentPublicIds = new ArrayList<>();
            }
            for (MultipartFile file : imageFiles) {
                if (file != null && !file.isEmpty()) {
                    try {
                        Map<String, String> uploadResult = cloudinaryService.uploadImage(file);
                        currentImages.add(uploadResult.get("url"));
                        currentPublicIds.add(uploadResult.get("publicId"));
                    } catch (Exception e) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body("Error uploading file [" + file.getOriginalFilename() + "]: " + e.getMessage());
                    }
                }
            }
            room.setImages(currentImages);
        }

        Room updatedRoom = roomRepo.save(room);
        return ResponseEntity.ok(mapToRoomResponseDto(updatedRoom));
    }


    @Transactional
    public ResponseEntity<?> deleteRoom(Long branchId, String roomIdOrPublic) {
        Optional<Room> roomOpt = findRoomByIdentifier(roomIdOrPublic);

        if (roomOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Error: Room not found for identifier: " + roomIdOrPublic);
        }

        Room room = roomOpt.get();

        if (!room.getBranch().getId().equals(branchId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Error: Cannot delete room. Cross-branch operations are blocked.");
        }

        List<String> publicIds = room.getPublicIds();

        for (String id: publicIds){
            try{
                cloudinaryService.deleteImage(id);
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting Image from Cloudinary");
            }
        }

        roomRepo.delete(room);
        return ResponseEntity.ok("Success: Room with identifier " + roomIdOrPublic + " has been completely deleted.");
    }

    private RoomResponseDto mapToRoomResponseDto(Room room) {
        RoomResponseDto dto = new RoomResponseDto();
        dto.setRoomNumber(room.getId());
        dto.setPricePerNight(room.getPricePerNight());
        if (room.getBranch() != null) {
            dto.setCurrency(room.getBranch().getCurrency());
        }
        dto.setAmenities(room.getAmenities());
        dto.setTags(room.getTags());

        if (room.getBranch() != null) {
            dto.setBranchId(room.getBranch().getId());
            dto.setBranchName(room.getBranch().getCity() + " Branch");
        }

        dto.setImages(room.getImages());
        dto.setPublicIds(room.getPublicIds());
        dto.setRoomId(room.getRoomId());

        if (room.getRoomType() != null) {
            dto.setRoomTypeName(room.getRoomType());
        }
        return dto;
    }

    /**
     * Resolve a room by either numeric DB id or the new public-facing roomId string.
     * If the identifier parses to a Long, attempt findById first, otherwise try findByRoomId.
     */
    private Optional<Room> findRoomByIdentifier(String id) {
        if (id == null) return Optional.empty();
        // Try numeric id lookup first
        try {
            Long numeric = Long.parseLong(id);
            Optional<Room> byId = roomRepo.findById(numeric);
            if (byId.isPresent()) return byId;
        } catch (NumberFormatException ignored) {
            // not numeric
        }
        // Fallback to roomId lookup
        return roomRepo.findByRoomId(id);
    }
}
