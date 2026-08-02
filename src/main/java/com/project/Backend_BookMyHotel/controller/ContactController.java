package com.project.Backend_BookMyHotel.controller;

import com.project.Backend_BookMyHotel.dto.ContactRequest;
import com.project.Backend_BookMyHotel.service.ContactService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/contact")
public class ContactController {

    @Autowired
    private ContactService contactService;

    @PostMapping
    public ResponseEntity<?> submitEnquiry(@Valid @RequestBody ContactRequest request) {
        return contactService.submitEnquiry(request);
    }
}
