package com.schwab.assessment.service;

/**
 * Thrown when a submitted URL fails format validation or is flagged as an
 * SSRF risk (targets a loopback, link-local, or private-range host).
 */
public class InvalidUrlException extends RuntimeException {

    public InvalidUrlException(String message) {
        super(message);
    }
}
