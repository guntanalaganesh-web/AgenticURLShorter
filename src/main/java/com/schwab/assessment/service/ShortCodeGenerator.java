package com.schwab.assessment.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates random base62 short codes for new links. Collision checking
 * against existing codes is the caller's responsibility ({@link UrlService}),
 * since only the repository knows what is already in use.
 */
@Component
public class ShortCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private final SecureRandom random = new SecureRandom();

    public String generate(int length) {
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
