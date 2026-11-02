package com.example.pulse.entity;

import com.example.pulse.enums.ChannelType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "alert_channels", indexes = @Index(name = "idx_channel_user", columnList = "user_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AlertChannel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChannelType type;

    /** an email address, or a webhook URL (Discord, Slack, ...) */
    @Column(nullable = false, length = 500)
    private String target;

    @Builder.Default
    private Instant createdAt = Instant.now();
}
