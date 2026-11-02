package com.example.pulse.exception;

/** Thrown when a URL is malformed or points somewhere we must not connect to (SSRF protection). */
public class UnsafeUrlException extends BadRequestException {
    public UnsafeUrlException(String message) { super(message); }
}
