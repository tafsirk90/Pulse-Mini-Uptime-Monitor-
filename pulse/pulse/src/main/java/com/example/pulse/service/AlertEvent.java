package com.example.pulse.service;

/** Describes a state change that users should be told about. */
public record AlertEvent(Long userId, String monitorName, String url, boolean down, String detail) {}
