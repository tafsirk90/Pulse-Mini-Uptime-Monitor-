package com.example.pulse.service;

import com.example.pulse.dto.Dtos.IncidentResponse;
import com.example.pulse.dto.Dtos.MonitorRequest;
import com.example.pulse.dto.Dtos.MonitorResponse;
import com.example.pulse.dto.Dtos.StatsResponse;
import com.example.pulse.entity.Incident;
import com.example.pulse.entity.Monitor;
import com.example.pulse.enums.MonitorState;
import com.example.pulse.exception.BadRequestException;
import com.example.pulse.exception.ResourceNotFoundException;
import com.example.pulse.net.UrlGuard;
import com.example.pulse.repository.CheckResultRepository;
import com.example.pulse.repository.IncidentRepository;
import com.example.pulse.repository.MonitorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MonitorService {

    private final MonitorRepository monitors;
    private final CheckResultRepository results;
    private final IncidentRepository incidents;
    private final StatsService stats;
    private final UrlGuard guard;

    @Value("${app.monitor.max-per-user}")
    private int maxPerUser;

    @Transactional(readOnly = true)
    public List<MonitorResponse> list(Long userId) {
        return monitors.findByUserIdOrderByCreatedAtAsc(userId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public MonitorResponse create(Long userId, MonitorRequest r) {
        if (monitors.countByUserId(userId) >= maxPerUser) {
            throw new BadRequestException("Limit reached: you can have up to " + maxPerUser + " monitors");
        }
        URI uri = guard.requirePublic(r.url());
        Monitor m = monitors.save(Monitor.builder()
                .userId(userId)
                .name(r.name().trim())
                .url(uri.toString())
                .intervalSec(r.intervalSec() == null ? 60 : r.intervalSec())
                .timeoutMs(r.timeoutMs() == null ? 10_000 : r.timeoutMs())
                .expectedStatus(r.expectedStatus())
                .keyword(blankToNull(r.keyword()))
                .nextCheckAt(Instant.now())   // first check happens on the next scheduler tick
                .build());
        return toResponse(m);
    }

    /** Editing changes what "up" means, so the monitor starts fresh: state reset and any open outage closed. */
    @Transactional
    public MonitorResponse update(Long userId, Long id, MonitorRequest r) {
        Monitor m = owned(userId, id);
        URI uri = guard.requirePublic(r.url());
        m.setName(r.name().trim());
        m.setUrl(uri.toString());
        m.setIntervalSec(r.intervalSec() == null ? 60 : r.intervalSec());
        m.setTimeoutMs(r.timeoutMs() == null ? 10_000 : r.timeoutMs());
        m.setExpectedStatus(r.expectedStatus());
        m.setKeyword(blankToNull(r.keyword()));
        resetState(m);
        m.setNextCheckAt(Instant.now());
        return toResponse(m);
    }

    /** Pausing ends any open outage (we cannot know the site's status while paused). */
    @Transactional
    public MonitorResponse setActive(Long userId, Long id, boolean active) {
        Monitor m = owned(userId, id);
        if (m.isActive() != active) {
            m.setActive(active);
            if (active) {
                m.setNextCheckAt(Instant.now());
            } else {
                resetState(m);
            }
        }
        return toResponse(m);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Monitor m = owned(userId, id);
        results.deleteAllForMonitor(id);
        incidents.deleteAllForMonitor(id);
        monitors.delete(m);
    }

    @Transactional(readOnly = true)
    public StatsResponse stats(Long userId, Long id, String range) {
        return stats.stats(owned(userId, id), range);
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> incidents(Long userId, Long id) {
        owned(userId, id);
        Instant now = Instant.now();
        return incidents.findTop50ByMonitorIdOrderByStartedAtDesc(id).stream().map(i -> {
            boolean ongoing = i.getResolvedAt() == null;
            long seconds = Duration.between(i.getStartedAt(), ongoing ? now : i.getResolvedAt()).getSeconds();
            return new IncidentResponse(i.getId(), i.getStartedAt(), i.getResolvedAt(), ongoing, seconds, i.getReason());
        }).toList();
    }

    private void resetState(Monitor m) {
        m.setState(MonitorState.UNKNOWN);
        m.setConsecutiveFailures(0);
        m.setFailingSince(null);
        m.setLastError(null);
        incidents.findFirstByMonitorIdAndResolvedAtIsNullOrderByStartedAtDesc(m.getId())
                .ifPresent((Incident i) -> i.setResolvedAt(Instant.now()));
    }

    private Monitor owned(Long userId, Long id) {
        return monitors.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Monitor not found"));
    }

    private MonitorResponse toResponse(Monitor m) {
        return new MonitorResponse(m.getId(), m.getName(), m.getUrl(), m.getIntervalSec(), m.getTimeoutMs(),
                m.getExpectedStatus(), m.getKeyword(), m.isActive(), m.displayStatus(),
                m.getLastCheckedAt(), m.getLastResponseMs(), m.getLastError(),
                stats.uptimePercent(m, Duration.ofHours(24)), stats.recentChecks(m.getId()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
