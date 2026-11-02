package com.example.pulse.entity;

import com.example.pulse.enums.MonitorState;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "monitors", indexes = @Index(name = "idx_monitor_user", columnList = "user_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Monitor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 500)
    private String url;

    private int intervalSec;
    private int timeoutMs;

    /** null means "any 2xx is fine" */
    private Integer expectedStatus;

    /** optional text that must appear in the response body */
    @Column(length = 100)
    private String keyword;

    @Builder.Default
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private MonitorState state = MonitorState.UNKNOWN;

    private int consecutiveFailures;
    private Instant failingSince;

    private Instant nextCheckAt;
    private Instant lastCheckedAt;
    private Long lastResponseMs;

    @Column(length = 300)
    private String lastError;

    @Builder.Default
    private Instant createdAt = Instant.now();

    /** UP, DOWN, PENDING (not enough data yet) or PAUSED */
    public String displayStatus() {
        if (!active) return "PAUSED";
        return state == MonitorState.UNKNOWN ? "PENDING" : state.name();
    }
}
