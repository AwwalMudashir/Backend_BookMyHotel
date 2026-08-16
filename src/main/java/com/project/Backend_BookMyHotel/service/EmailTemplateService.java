package com.project.Backend_BookMyHotel.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class EmailTemplateService {

    public record BookingServiceLine(
            String name,
            BigDecimal unitPrice,
            Integer quantity,
            BigDecimal subtotal
    ) {}

    public String userWelcomeTemplate(String name) {
        String escName = escapeHtml(name);

        return """
<div style="font-family: 'Helvetica Neue', Arial, sans-serif; padding:32px 16px; background:#f4f6f8; color:#111827;">
    <div style="max-width:600px; margin:auto; background:#ffffff; padding:32px; border-radius:12px; box-shadow:0 4px 25px rgba(17, 24, 39, 0.05); border-top: 6px solid #329775;">
        
        <div style="text-align: center; margin-bottom: 24px;">
            <h1 style="margin:0; font-size:28px; color:#111827; font-weight:700;">Welcome to Book My Hotel!</h1>
            <p style="margin:8px 0 0; color:#329775; font-size:16px; font-weight:600;">Your passport to effortless stays</p>
        </div>

        <p style="font-size:16px; line-height:1.6; color:#374151;">Hello %s,</p>
        
        <p style="font-size:15px; line-height:1.6; color:#4b5563;">
            We are thrilled to have you join our global community of travelers. Whether you are planning a weekend escape at the Marriott, a business trip at the Hilton, or discovering a hidden boutique branch, your next reservation is now just a few clicks away.
        </p>

        <div style="background:#f9fafb; border:1px solid #e5e7eb; border-radius:8px; padding:20px; margin:24px 0;">
            <h3 style="margin:0 0 10px; font-size:15px; color:#111827; font-weight:700;">What you can do right now:</h3>
            <ul style="margin:0; padding-left:20px; font-size:14px; color:#4b5563; line-height:1.8;">
                <li>Explore top-tier dynamic hotel brands globally</li>
                <li>Compare custom room packages and exclusive seasonal rates</li>
                <li>Manage multiple overlapping reservations inside one clean dashboard</li>
            </ul>
        </div>

        <div style="text-align: center; margin: 28px 0;">
            <a href="/explore" style="display:inline-block; background:#329775; color:#ffffff; padding:12px 32px; border-radius:6px; font-size:15px; font-weight:700; text-decoration:none;">Find Your Next Stay</a>
        </div>

        <hr style="margin:24px 0; border:0; border-top:1px solid #e5e7eb;"/>

        <p style="font-size:12px; color:#6b7280; text-align: center; margin:0;">
            Need assistance planning your itinerary? Our support desk is standing by 24/7.
        </p>
        
    </div>
</div>
""".formatted(escName);
    }

    public String otpTemplate(String name, String otpCode, int expiryMinutes) {
        String escName = escapeHtml(name);
        String escOtp = escapeHtml(otpCode);

        return """
<div style="font-family: 'Helvetica Neue', Arial, sans-serif; padding:32px 16px; background:#f4f6f8; color:#111827;">
    <div style="max-width:500px; margin:auto; background:#ffffff; padding:32px; border-radius:12px; box-shadow:0 4px 25px rgba(17, 24, 39, 0.05);">
        
        <div style="margin-bottom:24px; text-align: center;">
            <div style="display:inline-block; background:#f0fbf7; color:#329775; padding:6px 16px; border-radius:20px; font-size:12px; font-weight:700; letter-spacing:0.05em; text-transform:uppercase;">
                Security Verification
            </div>
            <h2 style="margin:12px 0 0; font-size:22px; color:#111827; font-weight:700;">Verify Your Identity</h2>
        </div>

        <p style="font-size:15px; color:#374151;">Hello %s,</p>
        <p style="font-size:14px; color:#4b5563; line-height:1.5;">
            Use the secure one-time password (OTP) listed below to complete your current action. This code is unique and single-use.
        </p>

        <div style="background:#111827; padding:24px; border-radius:8px; text-align:center; margin:24px 0; border: 1px solid #1f2937;">
            <p style="margin:0 0 8px; font-size:12px; color:#9ca3af; letter-spacing:0.05em; text-transform:uppercase;">Your Verification Code</p>
            <span style="font-size:36px; font-weight:800; color:#f9f871; letter-spacing:6px; font-family: monospace;">%s</span>
        </div>

        <p style="font-size:13px; color:#6b7280; text-align:center; margin-bottom:24px;">
            ⏰ This operational code will expire in <strong style="color:#111827;">%d minutes</strong>.
        </p>

        <hr style="margin:20px 0; border:0; border-top:1px solid #e5e7eb;"/>

        <p style="font-size:12px; color:#9ca3af; margin:0; line-height:1.4;">
            If you did not initiate this security verification action on Book My Hotel, please ignore this communication or contact technical administration immediately.
        </p>
        
    </div>
</div>
""".formatted(escName, escOtp, expiryMinutes);
    }

    public String passwordChangedTemplate(String name) {
        String escName = escapeHtml(name);

        return """
<div style="font-family: 'Helvetica Neue', Arial, sans-serif; padding:32px 16px; background:#f4f6f8; color:#111827;">
    <div style="max-width:500px; margin:auto; background:#ffffff; padding:32px; border-radius:12px; box-shadow:0 4px 25px rgba(17, 24, 39, 0.05); border-top: 6px solid #111827;">
        
        <h2 style="margin:0 0 16px 0; font-size:22px; color:#111827; font-weight:700;">Security Alert: Password Changed</h2>

        <p style="font-size:15px; color:#374151;">Hello %s,</p>
        
        <p style="font-size:14px; color:#4b5563; line-height:1.6;">
            This email confirms that the password for your <strong style="color:#329775;">Book My Hotel</strong> account was successfully updated recently.
        </p>

        <div style="background:#fffbeb; border:1px solid #fef3c7; border-radius:8px; padding:16px; margin:24px 0;">
            <p style="margin:0; font-size:14px; color:#b45309; font-weight:600; line-height:1.5;">
                Did you make this change?
            </p>
            <p style="margin:4px 0 0; font-size:13px; color:#78350f; line-height:1.5;">
                If you authorized this adjustment, you do not need to take any action. Your old password is now safely deprecated.
            </p>
        </div>

        <p style="font-size:14px; color:#4b5563; line-height:1.6;">
            <strong>If you did not change your password:</strong> Your account security may be compromised. Please use our recovery module immediately to lock down your credentials or contact support.
        </p>

        <div style="margin-top:28px; padding-top:20px; border-top:1px solid #e5e7eb; text-align: center;">
            <a href="/recovery" style="display:inline-block; background:#111827; color:#ffffff; padding:10px 24px; border-radius:6px; font-size:14px; font-weight:700; text-decoration:none;">Secure My Account</a>
        </div>
        
    </div>
</div>
""".formatted(escName);
    }

    public String bookingConfirmationTemplate(
            String guestName,
            String bookingRef,
            String hotelName,
            String branchLocation,
            String roomType,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            BigDecimal accommodationPrice,
            BigDecimal totalPrice,
            String currency,
            Integer ecoPointsRedeemed,
            BigDecimal ecoPointsDiscount,
            List<BookingServiceLine> services
    ) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy");
        String formattedCheckIn  = checkInDate.format(formatter);
        String formattedCheckOut = checkOutDate.format(formatter);
        long nights = ChronoUnit.DAYS.between(checkInDate, checkOutDate);
        String formattedPrice = currency + " " + totalPrice.setScale(2, RoundingMode.HALF_UP).toPlainString();
        String serviceDetails = bookingServicesSection(
                accommodationPrice, totalPrice, currency, ecoPointsRedeemed, ecoPointsDiscount, services);

        return """
<div style="font-family: 'Helvetica Neue', Arial, sans-serif; padding:32px 16px; background:#f4f6f8; color:#111827;">
    <div style="max-width:600px; margin:auto; background:#ffffff; padding:32px; border-radius:12px; box-shadow:0 4px 25px rgba(17, 24, 39, 0.05); border-top: 8px solid #329775;">

        <!-- Header -->
        <div style="margin-bottom:28px;">
            <span style="color:#329775; font-size:12px; letter-spacing:0.05em; text-transform:uppercase; font-weight:700;">Reservation Confirmed</span>
            <h1 style="margin:4px 0 0; font-size:26px; color:#111827; font-weight:800;">Pack Your Bags!</h1>
        </div>

        <p style="font-size:15px; color:#374151;">Hello %s,</p>
        <p style="font-size:14px; color:#4b5563; line-height:1.5; margin-bottom:24px;">
            Your booking has been confirmed. Below is your full reservation summary — save this email for your records.
        </p>

        <!-- Reference Summary Box -->
        <div style="background:#1f2937; color:#ffffff; padding:16px 20px; border-radius:8px; margin-bottom:24px;">
            <table width="100%%" border="0" cellspacing="0" cellpadding="0">
                <tr>
                    <td style="font-size:13px; color:#9ca3af;">BOOKING REFERENCE</td>
                    <td align="right" style="font-size:13px; color:#9ca3af;">TOTAL CHARGED</td>
                </tr>
                <tr>
                    <td style="font-size:20px; font-weight:800; color:#f9f871; font-family:monospace;">%s</td>
                    <td align="right" style="font-size:20px; font-weight:800; color:#ffffff;">%s</td>
                </tr>
            </table>
        </div>

        <!-- Accommodation Details -->
        <h3 style="font-size:13px; color:#111827; text-transform:uppercase; letter-spacing:0.06em; margin:0 0 12px; padding-bottom:6px; border-bottom:1px solid #e5e7eb;">Accommodation Details</h3>

        <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="margin-bottom:24px; font-size:14px; line-height:1.9;">
            <tr>
                <td style="padding:4px 0; color:#6b7280;" width="38%%">Hotel</td>
                <td style="padding:4px 0; color:#111827; font-weight:600;">%s</td>
            </tr>
            <tr>
                <td style="padding:4px 0; color:#6b7280;">Branch</td>
                <td style="padding:4px 0; color:#111827;">%s</td>
            </tr>
            <tr>
                <td style="padding:4px 0; color:#6b7280;">Room Type</td>
                <td style="padding:4px 0; color:#329775; font-weight:600; text-transform:capitalize;">%s</td>
            </tr>
            <tr>
                <td style="padding:4px 0; color:#6b7280;">Duration</td>
                <td style="padding:4px 0; color:#111827; font-weight:600;">%d night%s</td>
            </tr>
        </table>

        <!-- Stay Timeline -->
        <h3 style="font-size:13px; color:#111827; text-transform:uppercase; letter-spacing:0.06em; margin:0 0 12px; padding-bottom:6px; border-bottom:1px solid #e5e7eb;">Stay Duration</h3>

        <div style="background:#f9fafb; border:1px solid #e5e7eb; border-radius:8px; padding:16px; margin-bottom:28px;">
            <table width="100%%" border="0" cellspacing="0" cellpadding="0">
                <tr>
                    <td width="48%%" style="vertical-align:top;">
                        <span style="font-size:11px; color:#6b7280; text-transform:uppercase;">📅 Check-in</span><br/>
                        <strong style="font-size:15px; color:#111827;">%s</strong><br/>
                        <span style="font-size:12px; color:#6b7280;">After 3:00 PM</span>
                    </td>
                    <td width="4%%" style="border-left:1px solid #e5e7eb;">&nbsp;</td>
                    <td width="48%%" style="vertical-align:top; padding-left:10px;">
                        <span style="font-size:11px; color:#6b7280; text-transform:uppercase;">📅 Check-out</span><br/>
                        <strong style="font-size:15px; color:#111827;">%s</strong><br/>
                        <span style="font-size:12px; color:#6b7280;">Before 11:00 AM</span>
                    </td>
                </tr>
            </table>
        </div>

        %s

        <!-- CTA -->
        <div style="background:#f0fbf7; border:1px solid #a3d8c5; border-radius:8px; padding:16px; text-align:center;">
            <p style="margin:0; font-size:13px; color:#236952; line-height:1.4;">
                Need to modify your reservation or view your full booking details? Head to your dashboard at any time.
            </p>
            <a href="/my-bookings" style="display:inline-block; margin-top:12px; color:#329775; text-decoration:none; font-weight:700; font-size:14px;">View My Booking →</a>
        </div>

    </div>
</div>
""".formatted(
                escapeHtml(guestName),
                escapeHtml(bookingRef),
                formattedPrice,
                escapeHtml(hotelName),
                escapeHtml(branchLocation),
                escapeHtml(roomType),
                nights, nights == 1 ? "" : "s",
                formattedCheckIn,
                formattedCheckOut,
                serviceDetails
        );
    }

    private String bookingServicesSection(BigDecimal accommodationPrice, BigDecimal totalPrice,
                                          String currency, Integer ecoPointsRedeemed,
                                          BigDecimal ecoPointsDiscount, List<BookingServiceLine> services) {
        int redeemed = ecoPointsRedeemed != null ? ecoPointsRedeemed : 0;
        BigDecimal pointsDiscount = ecoPointsDiscount != null ? ecoPointsDiscount : BigDecimal.ZERO;
        if ((services == null || services.isEmpty()) && redeemed == 0) return "";

        StringBuilder rows = new StringBuilder();
        rows.append("<tr><td style=\"padding:7px 0;color:#6b7280;\">Accommodation</td>")
                .append("<td align=\"right\" style=\"padding:7px 0;color:#111827;\">")
                .append(formatMoney(currency, accommodationPrice)).append("</td></tr>");

        if (redeemed > 0) {
            rows.append("<tr><td style=\"padding:7px 0;color:#236952;\">Eco points discount ")
                    .append("<span style=\"font-size:11px;color:#6b7280;\">(")
                    .append(redeemed).append(" points)</span></td>")
                    .append("<td align=\"right\" style=\"padding:7px 0;color:#236952;font-weight:700;\">-")
                    .append(formatMoney(currency, pointsDiscount)).append("</td></tr>");
        }

        for (BookingServiceLine service : services != null ? services : List.<BookingServiceLine>of()) {
            rows.append("<tr><td style=\"padding:7px 0;color:#6b7280;\">")
                    .append(escapeHtml(service.name()))
                    .append(" &times; ").append(service.quantity())
                    .append(" <span style=\"font-size:11px;color:#9ca3af;\">(")
                    .append(formatMoney(currency, service.unitPrice())).append(" each)</span></td>")
                    .append("<td align=\"right\" style=\"padding:7px 0;color:#111827;\">")
                    .append(formatMoney(currency, service.subtotal())).append("</td></tr>");
        }

        return """
        <h3 style="font-size:13px; color:#111827; text-transform:uppercase; letter-spacing:0.06em; margin:0 0 12px; padding-bottom:6px; border-bottom:1px solid #e5e7eb;">Services &amp; Payment Details</h3>
        <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="margin-bottom:28px;font-size:14px;">
            %s
            <tr>
                <td style="padding:10px 0 4px;border-top:1px solid #e5e7eb;color:#111827;font-weight:700;">Total charged</td>
                <td align="right" style="padding:10px 0 4px;border-top:1px solid #e5e7eb;color:#329775;font-weight:800;">%s</td>
            </tr>
        </table>
        """.formatted(rows, formatMoney(currency, totalPrice));
    }

    private String formatMoney(String currency, BigDecimal amount) {
        BigDecimal safeAmount = amount != null ? amount : BigDecimal.ZERO;
        return escapeHtml(currency) + " " + safeAmount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public String bookingCancellationTemplate(
            String guestName,
            String bookingRef,
            String hotelName,
            String branchLocation,
            String roomType,
            LocalDate checkInDate,
            LocalDate cancellationDate,
            String refundAmount,
            boolean refundProcessed,
            boolean isEarlyCheckout
    ) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy");
        String formattedCheckIn = checkInDate.format(formatter);
        String formattedCancellation = cancellationDate.format(formatter);
        String formattedRefundAmount = refundAmount != null ? refundAmount : "Pending review";
        String refundHeadline = refundProcessed ? "Refund Processed" : "Cancellation Confirmed";
        String refundMessage = refundProcessed
                ? "We have processed your refund for <strong>" + escapeHtml(formattedRefundAmount) + "</strong>. The refunded amount should appear in your original payment method within 5-7 business days."
                : "Your booking has been cancelled successfully. If a payment was captured, your refund will be processed and should appear in your account within 5-7 business days. If you have any questions, please contact support.";
        String cancellationIntro = isEarlyCheckout
                ? "We’re sorry to see you check out early. Your reservation has been ended early and your room is now available for other guests."
                : "We’re sorry to see you cancel, but your reservation has been successfully cancelled before the check-in date.";

        return """
<div style="font-family: 'Helvetica Neue', Arial, sans-serif; padding:32px 16px; background:#f4f6f8; color:#111827;">
    <div style="max-width:600px; margin:auto; background:#ffffff; padding:32px; border-radius:12px; box-shadow:0 4px 25px rgba(17, 24, 39, 0.05); border-top: 8px solid #ef4444;">

        <!-- Header -->
        <div style="margin-bottom:28px;">
            <span style="color:#ef4444; font-size:12px; letter-spacing:0.05em; text-transform:uppercase; font-weight:700;">Booking Cancelled</span>
            <h1 style="margin:4px 0 0; font-size:26px; color:#111827; font-weight:800;">Your reservation has been cancelled</h1>
        </div>

        <p style="font-size:15px; color:#374151;">Hello %s,</p>
        <p style="font-size:14px; color:#4b5563; line-height:1.5; margin-bottom:24px;">
            %s
        </p>

        <div style="background:#fef2f2; border:1px solid #fee2e2; border-radius:8px; padding:18px 20px; margin-bottom:24px;">
            <p style="margin:0 0 10px; font-size:13px; color:#b91c1c; font-weight:700; text-transform:uppercase; letter-spacing:0.05em;">%s</p>
            <p style="margin:0; font-size:15px; color:#111827; line-height:1.7;">%s</p>
        </div>

        <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="margin-bottom:24px; font-size:14px; line-height:1.9;">
            <tr>
                <td style="padding:4px 0; color:#6b7280;" width="38%%">Booking Reference</td>
                <td style="padding:4px 0; color:#111827; font-weight:600;">%s</td>
            </tr>
            <tr>
                <td style="padding:4px 0; color:#6b7280;">Hotel</td>
                <td style="padding:4px 0; color:#111827;">%s</td>
            </tr>
            <tr>
                <td style="padding:4px 0; color:#6b7280;">Branch</td>
                <td style="padding:4px 0; color:#111827;">%s</td>
            </tr>
            <tr>
                <td style="padding:4px 0; color:#6b7280;">Room Type</td>
                <td style="padding:4px 0; color:#329775; font-weight:600; text-transform:capitalize;">%s</td>
            </tr>
            <tr>
                <td style="padding:4px 0; color:#6b7280;">Original Check-in</td>
                <td style="padding:4px 0; color:#111827;">%s</td>
            </tr>
            <tr>
                <td style="padding:4px 0; color:#6b7280;">Cancellation Date</td>
                <td style="padding:4px 0; color:#111827;">%s</td>
            </tr>
        </table>

        <div style="background:#f9fafb; border:1px solid #e5e7eb; border-radius:8px; padding:18px 20px; margin-bottom:28px;">
            <p style="margin:0; font-size:13px; color:#6b7280; text-transform:uppercase; letter-spacing:0.05em;">Refund Summary</p>
            <p style="margin:10px 0 0; font-size:15px; color:#111827; line-height:1.7;"><strong>%s</strong></p>
        </div>

        <div style="background:#f0fdf4; border:1px solid #bbf7d0; border-radius:8px; padding:16px; text-align:center;">
            <p style="margin:0; font-size:14px; color:#166534; line-height:1.6;">If you have any questions about your cancellation or refund, please contact our support team.</p>
            <a href="/support" style="display:inline-block; margin-top:14px; background:#22c55e; color:#ffffff; padding:12px 28px; border-radius:6px; font-size:14px; font-weight:700; text-decoration:none;">Contact Support</a>
        </div>

    </div>
</div>
""".formatted(
                escapeHtml(guestName),
                escapeHtml(cancellationIntro),
                escapeHtml(refundHeadline),
                refundMessage,
                escapeHtml(bookingRef),
                escapeHtml(hotelName),
                escapeHtml(branchLocation),
                escapeHtml(roomType),
                formattedCheckIn,
                formattedCancellation,
                escapeHtml(formattedRefundAmount)
        );
    }

    public String contactTemplate(String name, String email, String message) {
        String escName = escapeHtml(name);
        String escEmail = escapeHtml(email);
        String escMessage = escapeHtml(message).replace("\n", "<br/>");

        return """
<div style="font-family: 'Helvetica Neue', Arial, sans-serif; padding:32px 16px; background:#f4f6f8; color:#111827;">
    <div style="max-width:600px; margin:auto; background:#ffffff; padding:32px; border-radius:12px; box-shadow:0 4px 25px rgba(17, 24, 39, 0.05); border-top: 6px solid #329775;">
        
        <div style="margin-bottom:24px;">
            <p style="margin:0; color:#6b7280; font-size:12px; letter-spacing:0.05em; text-transform:uppercase; font-weight:700;">Book My Hotel • Customer Support</p>
            <h1 style="margin:8px 0 0; font-size:24px; color:#111827; font-weight:700;">New Contact Inquiry</h1>
        </div>

        <div style="background:#f9fafb; border:1px solid #e5e7eb; border-radius:8px; padding:16px; margin-bottom:20px;">
            <p style="margin:0 0 8px; font-size:15px; color:#374151;"><strong>Guest Name:</strong> %s</p>
            <p style="margin:0; font-size:15px; color:#374151;"><strong>Email Address:</strong> %s</p>
        </div>

        <div style="padding:20px; border-radius:8px; background:#f0fbf7; border:1px solid #a3d8c5;">
            <p style="margin:0 0 10px; font-size:14px; color:#329775; font-weight:700; text-transform:uppercase; letter-spacing:0.03em;">Message Content</p>
            <p style="margin:0; font-size:15px; line-height:1.6; color:#111827;">%s</p>
        </div>

        <div style="margin-top:28px; padding-top:20px; border-top:1px solid #e5e7eb; font-size:14px; color:#6b7280;">
            <p style="margin:0;">You can respond directly to the visitor at: <a href="mailto:%s" style="color:#329775; text-decoration:underline; font-weight:600;">%s</a>.</p>
        </div>
        
    </div>
</div>
""".formatted(escName, escEmail, escMessage, escEmail, escEmail);
    }

    public String adminWelcomeTemplate(
            String adminName,
            String email,
            String temporaryPassword
    ) {
        return """
<div style="font-family: 'Helvetica Neue', Arial, sans-serif; padding:32px 16px; background:#f4f6f8; color:#111827;">
    <div style="max-width:600px; margin:auto; background:#ffffff; padding:32px; border-radius:12px; box-shadow:0 4px 25px rgba(17, 24, 39, 0.05); border-top: 8px solid #111827;">

        <!-- Header -->
        <div style="margin-bottom:28px;">
            <span style="color:#6b7280; font-size:12px; letter-spacing:0.05em; text-transform:uppercase; font-weight:700;">System Administrator</span>
            <h1 style="margin:4px 0 0; font-size:26px; color:#111827; font-weight:800;">Admin access granted.</h1>
        </div>

        <p style="font-size:15px; color:#374151;">Hello %s,</p>
        <p style="font-size:14px; color:#4b5563; line-height:1.5; margin-bottom:24px;">
            A system administrator account has been provisioned for you on Book My Hotel. This account carries full platform access. Treat these credentials with the highest level of security.
        </p>

        <!-- Credentials Box -->
        <div style="background:#1f2937; color:#ffffff; padding:20px 24px; border-radius:8px; margin-bottom:24px;">
            <p style="margin:0 0 14px; font-size:12px; color:#9ca3af; text-transform:uppercase; letter-spacing:0.05em;">Your Login Credentials</p>
            <table width="100%%" border="0" cellspacing="0" cellpadding="0">
                <tr>
                    <td style="font-size:13px; color:#9ca3af; padding-bottom:6px;" width="35%%">Email</td>
                    <td style="font-size:14px; color:#ffffff; font-weight:600; padding-bottom:6px;">%s</td>
                </tr>
                <tr>
                    <td style="font-size:13px; color:#9ca3af;">Temp Password</td>
                    <td style="font-size:18px; font-weight:800; color:#f9f871; font-family:monospace; letter-spacing:2px;">%s</td>
                </tr>
            </table>
        </div>

        <!-- High Security Warning -->
        <div style="background:#fef2f2; border:1px solid #fecaca; border-radius:8px; padding:14px 16px; margin-bottom:24px;">
            <span style="font-size:18px;">🔒</span>
            <p style="margin:6px 0 0; font-size:13px; color:#991b1b; line-height:1.5;">
                <strong>Critical security notice:</strong> This account has unrestricted access to all platform data, hotels, reservations, and user accounts. Change your password immediately after your first login and never share these credentials.
            </p>
        </div>

        <!-- Access Level -->
        <h3 style="font-size:13px; color:#111827; text-transform:uppercase; letter-spacing:0.06em; margin:0 0 12px; padding-bottom:6px; border-bottom:1px solid #e5e7eb;">Account Privileges</h3>

        <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="margin-bottom:24px; font-size:14px; line-height:1.9;">
            <tr>
                <td style="padding:4px 0; color:#6b7280;" width="38%%">Role</td>
                <td style="padding:4px 0; color:#111827; font-weight:600;">System Administrator</td>
            </tr>
            <tr>
                <td style="padding:4px 0; color:#6b7280;">Scope</td>
                <td style="padding:4px 0; color:#111827;">Full platform — all hotels, branches, rooms</td>
            </tr>
            <tr>
                <td style="padding:4px 0; color:#6b7280;">Access Level</td>
                <td style="padding:4px 0; color:#991b1b; font-weight:600;">Unrestricted</td>
            </tr>
        </table>

        <!-- What Admin Can Do -->
        <div style="background:#f9fafb; border:1px solid #e5e7eb; border-radius:8px; padding:20px; margin-bottom:28px;">
            <h3 style="margin:0 0 10px; font-size:14px; color:#111827; font-weight:700;">Your admin capabilities:</h3>
            <ul style="margin:0; padding-left:20px; font-size:13px; color:#4b5563; line-height:1.9;">
                <li>Add, edit and remove hotels, branches and rooms</li>
                <li>Create and manage hotel manager accounts</li>
                <li>View and manage all reservations across all hotels</li>
                <li>Access full analytics — room nights, revenue, ADR per hotel</li>
                <li>Create and manage platform-wide promotional campaigns</li>
                <li>Moderate and remove guest reviews</li>
                <li>View all guest accounts and booking history</li>
            </ul>
        </div>

        <!-- CTA -->
        <div style="text-align:center; margin-bottom:28px;">
            <a href="/admin/dashboard" style="display:inline-block; background:#111827; color:#ffffff; padding:13px 36px; border-radius:6px; font-size:15px; font-weight:700; text-decoration:none;">
                Access Admin Panel
            </a>
        </div>

        <hr style="margin:24px 0; border:0; border-top:1px solid #e5e7eb;"/>

        <p style="font-size:12px; color:#6b7280; text-align:center; margin:0; line-height:1.6;">
            This email was generated by the Book My Hotel platform system.<br/>
            If you did not expect this, contact your system administrator immediately.
        </p>

    </div>
</div>
""".formatted(
                escapeHtml(adminName),
                escapeHtml(email),
                escapeHtml(temporaryPassword)
        );
    }

    public String hotelManagerWelcomeTemplate(
            String managerName,
            String email,
            String temporaryPassword,
            String hotelName
    ) {
        return """
<div style="font-family: 'Helvetica Neue', Arial, sans-serif; padding:32px 16px; background:#f4f6f8; color:#111827;">
    <div style="max-width:600px; margin:auto; background:#ffffff; padding:32px; border-radius:12px; box-shadow:0 4px 25px rgba(17, 24, 39, 0.05); border-top: 8px solid #329775;">

        <!-- Header -->
        <div style="margin-bottom:28px;">
            <span style="color:#329775; font-size:12px; letter-spacing:0.05em; text-transform:uppercase; font-weight:700;">Hotel Manager Account</span>
            <h1 style="margin:4px 0 0; font-size:26px; color:#111827; font-weight:800;">You have been onboarded.</h1>
        </div>

        <p style="font-size:15px; color:#374151;">Hello %s,</p>
        <p style="font-size:14px; color:#4b5563; line-height:1.5; margin-bottom:24px;">
            A manager account has been created for you on Book My Hotel. You have been assigned to manage
            <strong style="color:#111827;">%s</strong>. Use the credentials below to log in and access your hotel dashboard.
        </p>

        <!-- Credentials Box -->
        <div style="background:#1f2937; color:#ffffff; padding:20px 24px; border-radius:8px; margin-bottom:24px;">
            <p style="margin:0 0 14px; font-size:12px; color:#9ca3af; text-transform:uppercase; letter-spacing:0.05em;">Your Login Credentials</p>
            <table width="100%%" border="0" cellspacing="0" cellpadding="0">
                <tr>
                    <td style="font-size:13px; color:#9ca3af; padding-bottom:6px;" width="35%%">Email</td>
                    <td style="font-size:14px; color:#ffffff; font-weight:600; padding-bottom:6px;">%s</td>
                </tr>
                <tr>
                    <td style="font-size:13px; color:#9ca3af;">Temp Password</td>
                    <td style="font-size:18px; font-weight:800; color:#f9f871; font-family:monospace; letter-spacing:2px;">%s</td>
                </tr>
            </table>
        </div>

        <!-- Security Notice -->
        <div style="background:#fff8f0; border:1px solid #f5d9b0; border-radius:8px; padding:14px 16px; margin-bottom:24px; display:flex; gap:10px;">
            <span style="font-size:18px;">⚠️</span>
            <p style="margin:0; font-size:13px; color:#92400e; line-height:1.5;">
                This is a temporary password. You will be prompted to change it on your first login. Do not share this email with anyone.
            </p>
        </div>

        <!-- What They Manage -->
        <h3 style="font-size:13px; color:#111827; text-transform:uppercase; letter-spacing:0.06em; margin:0 0 12px; padding-bottom:6px; border-bottom:1px solid #e5e7eb;">Your Assigned Property</h3>

        <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="margin-bottom:24px; font-size:14px; line-height:1.9;">
            <tr>
                <td style="padding:4px 0; color:#6b7280;" width="38%%">Hotel</td>
                <td style="padding:4px 0; color:#111827; font-weight:600;">%s</td>
            </tr>
            <tr>
                <td style="padding:4px 0; color:#6b7280;">Role</td>
                <td style="padding:4px 0; color:#329775; font-weight:600;">Hotel Manager</td>
            </tr>
            <tr>
                <td style="padding:4px 0; color:#6b7280;">Access Level</td>
                <td style="padding:4px 0; color:#111827;">Rates, availability, services, reservations &amp; reviews</td>
            </tr>
        </table>

        <!-- What You Can Do -->
        <div style="background:#f9fafb; border:1px solid #e5e7eb; border-radius:8px; padding:20px; margin-bottom:28px;">
            <h3 style="margin:0 0 10px; font-size:14px; color:#111827; font-weight:700;">What you can do from your dashboard:</h3>
            <ul style="margin:0; padding-left:20px; font-size:13px; color:#4b5563; line-height:1.9;">
                <li>Update your hotel's property description and branch details</li>
                <li>Set and manage room rates and availability calendars</li>
                <li>Add and manage ancillary services (spa, car hire, tours, restaurant)</li>
                <li>Create promotional discount opportunities for guests</li>
                <li>View and track all reservations made at your hotel</li>
                <li>Monitor guest reviews submitted for your branches</li>
            </ul>
        </div>

        <!-- CTA -->
        <div style="text-align:center; margin-bottom:28px;">
            <a href="/manager/dashboard" style="display:inline-block; background:#329775; color:#ffffff; padding:13px 36px; border-radius:6px; font-size:15px; font-weight:700; text-decoration:none;">
                Access My Dashboard
            </a>
        </div>

        <hr style="margin:24px 0; border:0; border-top:1px solid #e5e7eb;"/>

        <p style="font-size:12px; color:#6b7280; text-align:center; margin:0; line-height:1.6;">
            If you did not expect this email or believe this account was created in error,<br/>
            please contact the platform administrator immediately.
        </p>

    </div>
</div>
""".formatted(
                escapeHtml(managerName),
                escapeHtml(hotelName),
                escapeHtml(email),
                escapeHtml(temporaryPassword),
                escapeHtml(hotelName)
        );
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    public String promotionAnnouncementTemplate(String promoCode, Object discountType, java.math.BigDecimal discountValue, String hotelName, String longImageUrl, java.time.LocalDate validTo) {
        String escHotel = escapeHtml(hotelName);
        String escCode = escapeHtml(promoCode);
        String value = discountValue == null ? "" : discountValue.toPlainString();

        String discountText = "";
        if (discountType != null && discountType.toString().equalsIgnoreCase("PERCENTAGE")) {
            discountText = value + "% off";
        } else if (discountType != null) {
            discountText = "$" + value + " off";
        }

        String dateText = validTo == null ? "" : "Valid until " + escapeHtml(validTo.toString());

        String hero = longImageUrl == null || longImageUrl.isBlank() ? "" : "<div style=\"width:100%;height:200px;background-image:url('" + escapeHtml(longImageUrl) + "');background-size:cover;background-position:center;border-radius:8px;margin-bottom:16px;\"></div>";

        return """
<div style="font-family: 'Helvetica Neue', Arial, sans-serif; padding:20px; background:#f4f6f8; color:#111827;">
  <div style="max-width:700px;margin:auto;background:#0b1220;color:#fff;padding:20px;border-radius:12px;overflow:hidden;">
    %s
    <div style="background:linear-gradient(180deg, rgba(0,0,0,0.35), rgba(0,0,0,0.55)); padding:18px; border-radius:8px;">
      <h2 style="margin:0 0 8px; font-size:20px;">%s</h2>
      <p style="margin:0 0 12px; font-size:16px; color:#e6eef0;">Use <strong style=\"background:#111827;padding:4px 8px;border-radius:6px;\">%s</strong> to get <strong>%s</strong> at <strong>%s</strong></p>
      <p style="font-size:13px; color:#cbd5db; margin:0;">%s</p>
    </div>
  </div>
</div>
""".formatted(hero, escHotel + " • Special Offer", escCode, discountText, escHotel, dateText);
    }

}
