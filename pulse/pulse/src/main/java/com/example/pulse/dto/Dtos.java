package com.example.pulse.dto;

import com.example.pulse.enums.ChannelType;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

/** All request and response objects in one place. */
public final class Dtos {
    private Dtos() {}

    // ---------- auth ----------
    public record RegisterRequest(
            @NotBlank(message = "Name is required") @Size(max = 100) String name,
            @NotBlank(message = "Email is required") @Email(message = "Enter a valid email") String email,
            @NotBlank(message = "Password is required")
            @Size(min = 6, max = 100, message = "Password must be at least 6 characters") String password) {}

    public record LoginRequest(
            @NotBlank(message = "Email is required") String email,
            @NotBlank(message = "Password is required") String password) {}

    public record AuthResponse(String token, String name, String email, String slug) {}

    // ---------- monitors ----------
    public record MonitorRequest(
            @NotBlank(message = "Name is required") @Size(max = 100) String name,
            @NotBlank(message = "URL is required") @Size(max = 500) String url,
            @Min(value = 10, message = "Interval must be at least 10 seconds")
            @Max(value = 3600, message = "Interval can be at most 3600 seconds") Integer intervalSec,
            @Min(value = 1000, message = "Timeout must be at least 1000 ms")
            @Max(value = 30000, message = "Timeout can be at most 30000 ms") Integer timeoutMs,
            @Min(value = 100, message = "Expected status must be between 100 and 599")
            @Max(value = 599, message = "Expected status must be between 100 and 599") Integer expectedStatus,
            @Size(max = 100, message = "Keyword is too long") String keyword) {}

    public record MonitorResponse(Long id, String name, String url, int intervalSec, int timeoutMs,
                                  Integer expectedStatus, String keyword, boolean active,
                                  String status, Instant lastCheckedAt, Long lastResponseMs,
                                  String lastError, double uptime24h, List<Boolean> recent) {}

    public record Point(long t, Long ms, boolean failed) {}

    public record StatsResponse(String range, double uptimePercent, Long avgMs, Long p95Ms,
                                long totalChecks, long failedChecks, long downtimeSeconds,
                                List<Point> points) {}

    public record IncidentResponse(Long id, Instant startedAt, Instant resolvedAt, boolean ongoing,
                                   long durationSeconds, String reason) {}

    // ---------- alert channels ----------
    public record ChannelRequest(
            @NotNull(message = "Type is required") ChannelType type,
            @NotBlank(message = "Target is required") @Size(max = 500) String target) {}

    public record ChannelResponse(Long id, ChannelType type, String target, Instant createdAt) {}

    public record MessageResponse(String message) {}

    // ---------- public status page ----------
    public record PublicMonitor(String name, String status, double uptime24h, double uptime30d,
                                List<Boolean> recent) {}

    public record PublicIncident(String monitorName, Instant startedAt, Instant resolvedAt,
                                 boolean ongoing, String reason) {}

    public record PublicStatus(String owner, int total, int up, int down,
                               List<PublicMonitor> monitors, List<PublicIncident> incidents) {}
}
