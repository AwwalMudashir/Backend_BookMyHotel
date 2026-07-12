package com.project.Backend_BookMyHotel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Service
public class ResendEmailService {

    @Value("${resend.api.key}")
    private String apiKey;

    @Value("${resend.email.from}")
    private String fromEmail;

    private static final Logger logger = LoggerFactory.getLogger(ResendEmailService.class);

    public void sendEmail(String to, String subject, String html) {
        sendEmail(to, subject, html, null);
    }

    public void sendContactEmail(String to, String replyTo, String subject, String html) {
        sendEmail(to, subject, html, replyTo);
    }

    public void sendEmail(String to, String subject, String html, String replyTo) {
        logger.info("[Email] ResendEmailService start: to={} from={} replyTo={} subject={}", to, fromEmail, replyTo, subject);

        if (apiKey == null || apiKey.isBlank()) {
            logger.error("[Email] Resend API key is missing. Set RESEND_API_KEY in the environment.");
            throw new IllegalStateException("Resend API key is not configured. Set RESEND_API_KEY.");
        }

        if (fromEmail == null || fromEmail.isBlank()) {
            logger.error("[Email] Resend from email is missing. Set RESEND_EMAIL_FROM in the environment.");
            throw new IllegalStateException("Resend from email is not configured. Set RESEND_EMAIL_FROM.");
        }

        try {
            URL url = new URL("https://api.resend.com/emails");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);

            String escapedSubject = escapeJson(subject);
            String escapedHtml = escapeJson(html);
            String escapedReplyTo = replyTo != null ? escapeJson(replyTo) : null;

            String replyField = escapedReplyTo != null && !escapedReplyTo.isBlank()
                    ? ",\n              \"reply_to\": \"%s\"".formatted(escapedReplyTo)
                    : "";

            String body = """
            {
              "from": "%s",
              "to": ["%s"],
              "subject": "%s",
              "html": "%s"%s
            }
            """.formatted(
                    escapeJson(fromEmail),
                    escapeJson(to),
                    escapedSubject,
                    escapedHtml,
                    replyField
            );

            logger.debug("[Email] Resend request body: {}", body);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            String responseMessage = conn.getResponseMessage();
            logger.info("[Email] Resend response code={} message={}", responseCode, responseMessage);

            if (responseCode >= 400) {
                String errorBody = readStream(conn.getErrorStream());
                logger.error("[Email] Resend failed code={} body={}", responseCode, errorBody);
                throw new RuntimeException("Resend failed with code: " + responseCode + " response: " + errorBody);
            }

            String successBody = readStream(conn.getInputStream());
            logger.info("[Email] Resend success response body={}", successBody);

        } catch (Exception e) {
            logger.error("[Email] ResendEmailService exception while sending email", e);
            throw new RuntimeException("Email sending failed", e);
        }
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String readStream(InputStream stream) {
        if (stream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString().trim();
        } catch (Exception e) {
            logger.warn("[Email] Failed to read response stream", e);
            return "";
        }
    }
}