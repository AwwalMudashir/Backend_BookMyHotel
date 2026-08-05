package com.project.Backend_BookMyHotel.service;

import com.project.Backend_BookMyHotel.domain.ContactEnquiry;
import com.project.Backend_BookMyHotel.dto.ContactRequest;
import com.project.Backend_BookMyHotel.repository.ContactEnquiryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ContactService {

    private static final Logger log = LoggerFactory.getLogger(ContactService.class);

    @Autowired
    private ContactEnquiryRepository contactEnquiryRepository;

    @Autowired
    private EmailTemplateService emailTemplateService;

    @Autowired
    private ResendEmailService resendEmailService;

    @Value("${app.email}")
    private String supportEmail;

    public ResponseEntity<?> submitEnquiry(ContactRequest request) {
        ContactEnquiry enquiry = new ContactEnquiry();
        enquiry.setName(request.name().trim());
        enquiry.setEmail(request.email().trim());
        enquiry.setMessage(request.message().trim());

        ContactEnquiry saved = contactEnquiryRepository.save(enquiry);

        String html = emailTemplateService.contactTemplate(saved.getName(), saved.getEmail(), saved.getMessage());
        boolean emailSent = resendEmailService.sendContactEmail(supportEmail, saved.getEmail(), "New Contact Inquiry from " + saved.getName(), html);

        if (!emailSent) {
            log.error("Failed to send contact notification email for enquiry {}", saved.getId());
            return ResponseEntity.status(HttpStatus.CREATED).header("X-Email-Failure", "contact_notification_failed").body(Map.of(
                    "message", "Your enquiry has been received. Our support team will get back to you shortly.",
                    "enquiryId", saved.getId()
            ));
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Your enquiry has been received. Our support team will get back to you shortly.",
                "enquiryId", saved.getId()
        ));
    }
}
