package com.example.pulse.net;

/** The result of one HTTP check. */
public record CheckOutcome(boolean success, Integer statusCode, long responseMs, String error) {}
