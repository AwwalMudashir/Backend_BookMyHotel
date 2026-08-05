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

    // Frontend base URL for link redirects inside email templates
    @Value("${frontend.base.url:http://localhost:5173}")
    private String frontendBaseUrl;

    private static final Logger logger = LoggerFactory.getLogger(ResendEmailService.class);

    public boolean sendEmail(String to, String subject, String html) {
        return sendEmail(to, subject, html, null);
    }

    public boolean sendContactEmail(String to, String replyTo, String subject, String html) {
        return sendEmail(to, subject, html, replyTo);
    }

    public boolean sendEmail(String to, String subject, String html, String replyTo) {
        logger.info("[Email] ResendEmailService start: to={} from={} replyTo={} subject={}", to, fromEmail, replyTo, subject);

        if (apiKey == null || apiKey.isBlank()) {
            logger.error("[Email] Resend API key is missing. Set RESEND_API_KEY in the environment.");
            return false;
        }

        if (fromEmail == null || fromEmail.isBlank()) {
            logger.error("[Email] Resend from email is missing. Set RESEND_EMAIL_FROM in the environment.");
            return false;
        }

        try {
            // Rewrite relative frontend links in the template to point at the configured frontend base URL
            String processedHtml = rewriteFrontendLinks(html);

            URL url = new URL("https://api.resend.com/emails");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);

            String escapedSubject = escapeJson(subject);
            String escapedHtml = escapeJson(processedHtml);
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
                return false;
            }

            String successBody = readStream(conn.getInputStream());
            logger.info("[Email] Resend success response body={}", successBody);
            return true;

        } catch (Exception e) {
            logger.error("[Email] ResendEmailService exception while sending email", e);
            return false;
        }
    }

    private String rewriteFrontendLinks(String html) {
        if (html == null) return null;
        String base = frontendBaseUrl != null ? frontendBaseUrl.replaceAll("/$", "") : "http://localhost:5173";

        // Specific mappings
        html = html.replace("href=\"/explore\"", "href=\"" + base + "/search\"");
        html = html.replace("href=\"/recovery\"", "href=\"" + base + "/profile\"");
        html = html.replace("href=\"/my-bookings\"", "href=\"" + base + "/my-bookings\"");
        html = html.replace("href=\"/support\"", "href=\"" + base + "/contact\"");
        html = html.replace("href=\"/admin/dashboard\"", "href=\"" + base + "/admin/dashboard\"");
        html = html.replace("href=\"/manager/dashboard\"", "href=\"" + base + "/manager/dashboard\"");

        // Generic mapping for remaining absolute-path hrefs (avoid mailto: and protocol-based links)
        try {
            html = html.replaceAll("href=\\\"/(?!/)([a-zA-Z0-9_\\-/:]+)\\\"",
                    "href=\"" + base + "/$1\"");
        } catch (Exception ignored) {
            // If regex fails for any reason, fall back to what we have
        }

        return html;
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