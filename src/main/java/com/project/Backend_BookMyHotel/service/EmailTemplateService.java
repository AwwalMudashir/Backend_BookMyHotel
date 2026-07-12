package com.project.Backend_BookMyHotel.service;

import org.springframework.stereotype.Service;

@Service
public class EmailTemplateService {

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
            String checkInDate,
            String checkOutDate,
            String totalPrice
    ) {
        return """
<div style="font-family: 'Helvetica Neue', Arial, sans-serif; padding:32px 16px; background:#f4f6f8; color:#111827;">
    <div style="max-width:600px; margin:auto; background:#ffffff; padding:32px; border-radius:12px; box-shadow:0 4px 25px rgba(17, 24, 39, 0.05); border-top: 8px solid #329775;">
        
        <!-- Header -->
        <div style="margin-bottom:28px;">
            <span style="color:#329775; font-size:12px; letter-spacing:0.05em; text-transform:uppercase; font-weight:700;">Reservation Confirmed</span>
            <h1 style="margin:4px 0 0; font-size:26px; color:#111827; font-weight:800;">Pack Your Bags! 🏨</h1>
        </div>

        <p style="font-size:15px; color:#374151;">Hello %s,</p>
        <p style="font-size:14px; color:#4b5563; line-height:1.5; margin-bottom:24px;">
            Your booking request has been systematically processed and confirmed by our channel servers. Below is your detailed structural itinerary receipt:
        </p>

        <!-- Reference Summary Box -->
        <div style="background:#1f2937; color:#ffffff; padding:16px 20px; border-radius:8px; margin-bottom:24px;">
            <table width="100%%" border="0" cellspacing="0" cellpadding="0">
                <tr>
                    <td style="font-size:13px; color:#9ca3af;">BOOKING REFERENCE</td>
                    <td align="right" style="font-size:13px; color:#9ca3af;">TOTAL CHARGE</td>
                </tr>
                <tr>
                    <td style="font-size:20px; font-weight:800; color:#f9f871; font-family:monospace;">%s</td>
                    <td align="right" style="font-size:20px; font-weight:800; color:#ffffff;">%s</td>
                </tr>
            </table>
        </div>

        <!-- Hotel & Room Specifics -->
        <h3 style="font-size:14px; color:#111827; text-transform:uppercase; letter-spacing:0.03em; margin:0 0 12px; padding-bottom:6px; border-bottom:1px solid #e5e7eb;">Accommodation Metrics</h3>
        
        <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="margin-bottom:24px; font-size:14px; line-height:1.8;">
            <tr>
                <td style="padding:4px 0; color:#6b7280;" width="35%%">Hotel Brand:</td>
                <td style="padding:4px 0; color:#111827; font-weight:600;">%s</td>
            </tr>
            <tr>
                <td style="padding:4px 0; color:#6b7280;">Branch Location:</td>
                <td style="padding:4px 0; color:#111827;">%s</td>
            </tr>
            <tr>
                <td style="padding:4px 0; color:#6b7280;">Selected Tier:</td>
                <td style="padding:4px 0; color:#329775; font-weight:600; text-transform:capitalize;">%s</td>
            </tr>
        </table>

        <!-- Check-In / Check-Out Schedule -->
        <h3 style="font-size:14px; color:#111827; text-transform:uppercase; letter-spacing:0.03em; margin:0 0 12px; padding-bottom:6px; border-bottom:1px solid #e5e7eb;">Stay Duration Timeline</h3>
        
        <div style="background:#f9fafb; border:1px solid #e5e7eb; border-radius:8px; padding:16px; margin-bottom:28px;">
            <table width="100%%" border="0" cellspacing="0" cellpadding="0">
                <tr>
                    <td width="48%%" style="vertical-align: top;">
                        <span style="font-size:11px; color:#6b7280; text-transform:uppercase;">📅 CHECK-IN</span><br/>
                        <strong style="font-size:15px; color:#111827;">%s</strong><br/>
                        <span style="font-size:12px; color:#6b7280;">After 3:00 PM</span>
                    </td>
                    <td width="4%%" style="border-left:1px solid #e5e7eb;">&nbsp;</td>
                    <td width="48%%" style="vertical-align: top; padding-left:10px;">
                        <span style="font-size:11px; color:#6b7280; text-transform:uppercase;">📅 CHECK-OUT</span><br/>
                        <strong style="font-size:15px; color:#111827;">%s</strong><br/>
                        <span style="font-size:12px; color:#6b7280;">Before 11:00 AM</span>
                    </td>
                </tr>
            </table>
        </div>

        <!-- Call to Action / Support Footer -->
        <div style="background:#f0fbf7; border:1px solid #a3d8c5; border-radius:8px; padding:16px; text-align:center;">
            <p style="margin:0; font-size:13px; color:#236952; line-height:1.4;">
                Need to modify your reservation timelines, add specific amenities, or check special cancellation rules? Log into your dashboard console instantly.
            </p>
            <a href="/dashboard/reservations" style="display:inline-block; margin-top:12px; color:#329775; text-decoration:none; font-weight:700; font-size:14px;">Modify Reservation →</a>
        </div>
        
    </div>
</div>
""".formatted(
                escapeHtml(guestName),
                escapeHtml(bookingRef),
                escapeHtml(totalPrice),
                escapeHtml(hotelName),
                escapeHtml(branchLocation),
                escapeHtml(roomType),
                escapeHtml(checkInDate),
                escapeHtml(checkOutDate)
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

    public String adminWelcomeTemplate(String username) {
        return """
<div style="font-family: 'Helvetica Neue', Arial, sans-serif; padding:32px 16px; background:#111827; color:#ffffff;">
    <div style="max-width:600px; margin:auto; padding:32px; border-radius:12px; background:#1f2937; border-left: 6px solid #329775; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
        
        <div style="display:inline-block; background:#329775; color:#ffffff; padding:4px 12px; border-radius:20px; font-size:11px; font-weight:700; letter-spacing:0.05em; text-transform:uppercase; margin-bottom:20px;">
            Management Portal
        </div>
        
        <h2 style="color:#ffffff; margin:0 0 16px 0; font-size:26px; font-weight:700;">Welcome to Book My Hotel</h2>

        <p style="font-size:16px; color:#e5e7eb; line-height:1.5;">Hello <span style="color:#f9f871; font-weight:700;">%s</span>,</p>

        <p style="font-size:15px; color:#9ca3af; margin-bottom:20px;">Your profile has been granted <strong style="color:#ffffff;">Admin Access</strong>. You now possess permissions to oversee platform operations:</p>

        <ul style="padding-left:20px; color:#e5e7eb; font-size:15px; line-height:1.8; margin-bottom:24px;">
            <li>Add, edit, or remove hotel listings and unique regional branches</li>
            <li>Manage calendar availability, occupancy controls, and room daily rates</li>
            <li>Access the Admin Analytics Dashboard (track room revenue and booked room nights)</li>
        </ul>

        <div style="background:#111827; padding:16px; border-radius:8px; border:1px solid #374151; margin-bottom:24px; text-align:center;">
            <p style="margin:0; font-size:15px; color:#9ca3af;">
                Access your console securely at: <a href="/admin" style="color:#329775; text-decoration:none; font-weight:700; font-size:16px;">/admin</a>
            </p>
        </div>

        <hr style="margin:20px 0; border:0; border-top:1px solid #374151;"/>

        <p style="font-size:12px; color:#9ca3af; margin:0;">
            🔒 Security Notice: Keep your login credentials confidential. All administrative platform changes are securely audited.
        </p>

    </div>
</div>
""".formatted(username);
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

}
