package com.project.Backend_BookMyHotel.service_tests;

import com.project.Backend_BookMyHotel.domain.ContactEnquiry;
import com.project.Backend_BookMyHotel.dto.ContactRequest;
import com.project.Backend_BookMyHotel.repository.ContactEnquiryRepository;
import com.project.Backend_BookMyHotel.service.ContactService;
import com.project.Backend_BookMyHotel.service.EmailTemplateService;
import com.project.Backend_BookMyHotel.service.ResendEmailService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class ContactServiceTest {

    @Mock
    private ContactEnquiryRepository contactEnquiryRepository;

    @Mock
    private EmailTemplateService emailTemplateService;

    @Mock
    private ResendEmailService resendEmailService;

    @InjectMocks
    private ContactService contactService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(contactService, "supportEmail", "support@bookmyhotel.test");
    }

    private ContactRequest request() {
        return new ContactRequest("Jane Doe", "jane@example.com", "Do you have rooms in July?");
    }

    @Test
    void submitEnquiry_SavesEnquiryAndNotifiesSupport() {
        Mockito.when(contactEnquiryRepository.save(Mockito.any(ContactEnquiry.class))).thenAnswer(inv -> {
            ContactEnquiry e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });
        Mockito.when(emailTemplateService.contactTemplate(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn("<html>enquiry</html>");

        ResponseEntity<?> response = contactService.submitEnquiry(request());

        Assertions.assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Mockito.verify(contactEnquiryRepository).save(Mockito.any(ContactEnquiry.class));
        Mockito.verify(resendEmailService).sendContactEmail(
                "support@bookmyhotel.test", "jane@example.com", "New Contact Inquiry from Jane Doe", "<html>enquiry</html>");

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        Assertions.assertEquals(1L, body.get("enquiryId"));
    }

    @Test
    void submitEnquiry_WhenNotificationEmailFails_EnquiryIsStillSavedAndRequestStillSucceeds() {
        Mockito.when(contactEnquiryRepository.save(Mockito.any(ContactEnquiry.class))).thenAnswer(inv -> {
            ContactEnquiry e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });
        Mockito.when(emailTemplateService.contactTemplate(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn("<html>enquiry</html>");
        Mockito.doThrow(new RuntimeException("Resend is down"))
                .when(resendEmailService).sendContactEmail(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString());

        ResponseEntity<?> response = contactService.submitEnquiry(request());

        // The DB save already happened before the email attempt — a notification failure must not
        // turn an already-successful submission into a 500 for the visitor.
        Assertions.assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Mockito.verify(contactEnquiryRepository).save(Mockito.any(ContactEnquiry.class));
    }
}
