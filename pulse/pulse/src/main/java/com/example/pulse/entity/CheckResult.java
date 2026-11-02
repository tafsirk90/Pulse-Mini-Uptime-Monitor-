package com.example.pulse.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "check_results",
        indexes = @Index(name = "idx_check_monitor_time", columnList = "monitor_id, checked_at"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CheckResult {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "monitor_id", nullable = false)
    private Long monitorId;

    private boolean success;
    private Integer statusCode;
    private long responseMs;

    @Column(length = 300)
    private String error;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;
}
