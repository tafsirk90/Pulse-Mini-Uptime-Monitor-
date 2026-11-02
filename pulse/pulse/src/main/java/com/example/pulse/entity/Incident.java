package com.example.pulse.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** One outage: opened when a monitor goes DOWN, closed when it recovers. */
@Entity
@Table(name = "incidents", indexes = @Index(name = "idx_incident_monitor", columnList = "monitor_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Incident {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "monitor_id", nullable = false)
    private Long monitorId;

    @Column(nullable = false)
    private Instant startedAt;

    /** null while the outage is still ongoing */
    private Instant resolvedAt;

    @Column(length = 300)
    private String reason;
}
