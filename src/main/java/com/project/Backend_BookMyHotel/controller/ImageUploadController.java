package com.project.Backend_BookMyHotel.controller;

import com.project.Backend_BookMyHotel.service.CloudinaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/upload")
public class ImageUploadController {

    private final CloudinaryService cloudinaryService;

    @Autowired
    public ImageUploadController(CloudinaryService cloudinaryService) {
        this.cloudinaryService = cloudinaryService;
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @PostMapping("/image")
    public ResponseEntity<Map<String, String>> uploadImage(@RequestPart("image") MultipartFile image) {
        Map<String, String> uploadResult = cloudinaryService.uploadImage(image);
        return ResponseEntity.ok(Map.of(
                "url", uploadResult.get("url"),
                "publicId", uploadResult.get("publicId")
        ));
    }
}
