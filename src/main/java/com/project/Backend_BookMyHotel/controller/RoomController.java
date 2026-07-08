package com.project.Backend_BookMyHotel.controller;

import com.project.Backend_BookMyHotel.domain.RoomType;
import com.project.Backend_BookMyHotel.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
        List<String> categories = new ArrayList<>(Arrays.asList("basic", "standard", "suite"));
        return ResponseEntity.ok(categories);
    }

}
