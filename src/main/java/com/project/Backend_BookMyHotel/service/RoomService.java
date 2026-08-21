package com.project.Backend_BookMyHotel.service;

import com.project.Backend_BookMyHotel.domain.Branch;
import com.project.Backend_BookMyHotel.domain.Room;
import com.project.Backend_BookMyHotel.domain.RoomType;
import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.BookingStatus;
import com.project.Backend_BookMyHotel.dto.RoomRequestDto;
import com.project.Backend_BookMyHotel.dto.RoomResponseDto;
import com.project.Backend_BookMyHotel.repository.BookingRepository;
import com.project.Backend_BookMyHotel.repository.BranchRepository;
import com.project.Backend_BookMyHotel.repository.RoomAvailabilityRepository;
import com.project.Backend_BookMyHotel.repository.RoomRepository;
import com.project.Backend_BookMyHotel.repository.RoomTypesRepository;
import com.project.Backend_BookMyHotel.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.time.LocalDate;
import java.util.EnumSet;
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
    private ExchangeRateService exchangeRateService;

    @Autowired
    private BookingRepository bookingRepo;

    @Autowired
    private RoomAvailabilityRepository availabilityRepo;

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

        List<Room> rooms = roomRepo.findByBranchIdAndActiveTrue(branchId);
        List<RoomResponseDto> dtoList = rooms.stream().map(this::mapToRoomResponseDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtoList);
    }

    @Transactional
    public ResponseEntity<?> getRoomById(String roomIdOrPublic) {
        Optional<Room> roomOpt = findRoomByIdentifier(roomIdOrPublic);
        if (roomOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Error: Room with identifier " + roomIdOrPublic + " not found.");
        }
        Room room = roomOpt.get();
        if (Boolean.FALSE.equals(room.getActive())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Error: This room is no longer available.");
        }
        return ResponseEntity.ok(mapToRoomResponseDto(room));
    }

    @Transactional
    @CacheEvict(value = "availability", allEntries = true)
    @PreAuthorize("hasAnyAuthority('ADMIN','HOTEL_MANAGER')")
    public ResponseEntity<?> createRoom(Long branchId, RoomRequestDto request, List<MultipartFile> imageFiles) {
        Optional<Branch> branchOpt = branchRepo.findById(branchId);

        if (branchOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Error: Branch with ID " + branchId + " not found.");
        }

        String roomTypeName = request.getRoomType();
        if ((roomTypeName == null || roomTypeName.isBlank()) && request.getRoomTypeId() != null) {
            Optional<RoomType> typeOpt = roomTypesRepo.findById(request.getRoomTypeId());
            if (typeOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Error: Assigned RoomType ID " + request.getRoomTypeId() + " does not exist.");
            }
            roomTypeName = typeOpt.get().getName();
        }
        if (roomTypeName == null || roomTypeName.isBlank()) {
            return ResponseEntity.badRequest().body("Error: Room type is required.");
        }

        Room room = new Room();
        room.setActive(true);
        room.setBranch(branchOpt.get());
        room.setRoomType(roomTypeName.trim());
        room.setDescription(request.getDescription());
        room.setMaxOccupancy(request.getMaxOccupancy());
        room.setPricePerNight(request.getPricePerNight());
        // Persist per-room currency when provided; otherwise default to branch currency for now
        if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
            room.setCurrency(exchangeRateService.requireSupportedCurrency(request.getCurrency()));
        } else if (branchOpt.get().getCurrency() != null) {
            room.setCurrency(exchangeRateService.requireSupportedCurrency(branchOpt.get().getCurrency()));
        }
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

        // Append any externally-provided image URLs (admin-entered). These are plain URLs and do not have publicIds.
        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            imageUrls.addAll(request.getImageUrls());
        }

        room.setImages(imageUrls);
        // Only set publicIds for actual uploaded images
        room.setPublicIds(publicIds);

        Room savedRoom = roomRepo.save(room);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapToRoomResponseDto(savedRoom));
    }

    @Transactional
    @CacheEvict(value = "availability", allEntries = true)
    public ResponseEntity<?> updateRoom(Long branchId, String roomIdOrPublic, RoomRequestDto request, List<MultipartFile> imageFiles) {
        if (!branchRepo.existsById(branchId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Error: Target Branch not found.");
        }

        Optional<Room> roomOpt = findRoomByIdentifier(roomIdOrPublic);
        if (roomOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Error: Room not found for identifier: " + roomIdOrPublic);
        }

        Room room = roomOpt.get();

        if (Boolean.FALSE.equals(room.getActive())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Error: This room has been removed from public listings and cannot be updated.");
        }

        // Security Check: Enforce branch ownership integrity
        if (!room.getBranch().getId().equals(branchId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Error: Security Violation. Room identifier " + roomIdOrPublic + " does not belong to Branch ID " + branchId + ".");
        }

        if (request.getRoomTypeId() != null) {
            Optional<RoomType> typeOpt = roomTypesRepo.findById(request.getRoomTypeId());
            if (typeOpt.isEmpty()) return ResponseEntity.badRequest().body("Error: Invalid RoomType ID.");
            room.setRoomType(typeOpt.get().getName());
        } else if (request.getRoomType() != null && !request.getRoomType().isBlank()) {
            room.setRoomType(request.getRoomType().trim());
        }

        room.setPricePerNight(request.getPricePerNight());
        if (request.getDescription() != null) room.setDescription(request.getDescription());
        if (request.getMaxOccupancy() != null) room.setMaxOccupancy(request.getMaxOccupancy());
        // Allow updating per-room currency when supplied by the admin UI
        if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
            room.setCurrency(exchangeRateService.requireSupportedCurrency(request.getCurrency()));
        }
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
            // append any image URLs provided in the JSON payload (external URLs)
            if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
                currentImages.addAll(request.getImageUrls());
            }
            room.setImages(currentImages);
            room.setPublicIds(currentPublicIds);
        } else {
            // even if no new files, allow adding image URLs via JSON payload
            if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
                List<String> currentImages = room.getImages();
                if (currentImages == null) currentImages = new ArrayList<>();
                currentImages.addAll(request.getImageUrls());
                room.setImages(currentImages);
            }
        }

        Room updatedRoom = roomRepo.save(room);
        return ResponseEntity.ok(mapToRoomResponseDto(updatedRoom));
    }

    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMIN','HOTEL_MANAGER')")
    public ResponseEntity<?> deleteRoomImage(Long branchId, String roomIdOrPublic, String publicId, String url) {
        if (!branchRepo.existsById(branchId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Error: Target Branch not found.");
        }

        Optional<Room> roomOpt = findRoomByIdentifier(roomIdOrPublic);
        if (roomOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Error: Room not found for identifier: " + roomIdOrPublic);
        }

        Room room = roomOpt.get();
        if (!room.getBranch().getId().equals(branchId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Error: Security Violation. Room does not belong to branch.");
        }

        List<String> publicIds = room.getPublicIds();
        List<String> images = room.getImages();
        if ((publicIds == null || publicIds.isEmpty()) && (images == null || images.isEmpty())) {
            return ResponseEntity.badRequest().body("Error: Room has no images to delete.");
        }

        // If a URL is provided, remove matching image URL (no Cloudinary action required)
        if (url != null && !url.isBlank()) {
            if (images == null) images = new ArrayList<>();
            int idx = images.indexOf(url);
            if (idx < 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Error: Image URL not found on this room.");
            }
            images.remove(idx);
            // If publicIds list exists and index aligns, remove that entry as well to keep arrays consistent
            if (publicIds != null && publicIds.size() > idx) {
                publicIds.remove(idx);
            }
            room.setImages(images);
            room.setPublicIds(publicIds);
            roomRepo.save(room);
            return ResponseEntity.ok("Success: Image URL removed from room.");
        }

        // Otherwise, expect a publicId for Cloudinary image
        if (publicId == null || publicId.isBlank()) {
            return ResponseEntity.badRequest().body("Error: Either publicId or url must be provided.");
        }

        if (publicIds == null || images == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Error: Image not found on this room.");
        }

        int idx = publicIds.indexOf(publicId);
        if (idx < 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Error: Image not found on this room.");
        }

        try {
            cloudinaryService.deleteImage(publicId);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting image from Cloudinary: " + e.getMessage());
        }

        // remove from arrays and save
        publicIds.remove(idx);
        images.remove(idx);
        room.setPublicIds(publicIds);
        room.setImages(images);
        roomRepo.save(room);

        return ResponseEntity.ok("Success: Image removed from room.");
    }


    @Transactional
    @CacheEvict(value = "availability", allEntries = true)
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

        if (Boolean.FALSE.equals(room.getActive())) {
            return ResponseEntity.ok("Success: Room is already removed from public listings.");
        }

        boolean hasActiveBookings = bookingRepo.existsByRoom_IdAndStatusInAndCheckOutAfter(
                room.getId(),
                EnumSet.of(BookingStatus.PENDING, BookingStatus.CONFIRMED),
                LocalDate.now()
        );

        if (hasActiveBookings) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    "This room cannot be removed because it has a pending or confirmed booking " +
                            "that has not checked out yet. Cancel or complete those bookings first."
            );
        }

        // Preserve the room row and every historical booking that points to it. Availability
        // overrides are operational data only, so they can be removed once the room is hidden.
        room.setActive(false);
        availabilityRepo.deleteByRoomId(room.getId());
        roomRepo.save(room);

        return ResponseEntity.ok(
                "Success: Room removed from public listings. Existing booking history has been preserved."
        );
    }

    private RoomResponseDto mapToRoomResponseDto(Room room) {
        RoomResponseDto dto = new RoomResponseDto();
        dto.setRoomNumber(room.getId());
        dto.setPricePerNight(room.getPricePerNight());
        if (room.getCurrency() != null && !room.getCurrency().isBlank()) {
            dto.setCurrency(room.getCurrency());
        } else if (room.getBranch() != null) {
            dto.setCurrency(room.getBranch().getCurrency());
        }
        dto.setAmenities(room.getAmenities());
        dto.setDescription(room.getDescription());
        dto.setMaxOccupancy(room.getMaxOccupancy());
        dto.setTags(room.getTags());

        if (room.getBranch() != null) {
            dto.setBranchId(room.getBranch().getId());
            dto.setBranchName(room.getBranch().getCity() + " Branch");
            if (room.getBranch().getHotel() != null) {
                dto.setHotelId(room.getBranch().getHotel().getId());
            }
        }

        dto.setImages(room.getImages());
        dto.setPublicIds(room.getPublicIds());
        dto.setRoomId(room.getRoomId());
        dto.setActive(room.getActive());

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
