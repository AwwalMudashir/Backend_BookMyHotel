package com.project.Backend_BookMyHotel.util;

import java.util.Random;

/**
 * Utility for generating public reference IDs for sensitive entities.
 * These are randomly-generated strings used in URLs to prevent exposing database IDs.
 */
public class PublicIdGenerator {
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int PUBLIC_ID_LENGTH = 8;
    private static final Random random = new Random();

    /**
     * Generate a random 8-character public ID for use in URLs.
     * Includes uppercase, lowercase, and digits.
     * Example: "aBc12DeF"
     */
    public static String generate() {
        StringBuilder id = new StringBuilder(PUBLIC_ID_LENGTH);
        for (int i = 0; i < PUBLIC_ID_LENGTH; i++) {
            id.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return id.toString();
    }
}
