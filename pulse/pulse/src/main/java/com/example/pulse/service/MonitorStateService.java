package com.example.pulse.service;

import com.example.pulse.entity.CheckResult;
import com.example.pulse.entity.Incident;
import com.example.pulse.entity.Monitor;
import com.example.pulse.enums.MonitorState;
import com.example.pulse.net.CheckOutcome;
import com.example.pulse.repository.CheckResultRepository;
import com.example.pulse.repository.IncidentRepository;
import com.example.pulse.repository.MonitorRepository;
import com.example.pulse.util.Formats;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The heart of the app: decides which monitors are due, and turns each check result into state changes.
 *
 * State machine (failure threshold N, default 3):
 *   UP/UNKNOWN + N failures in a row -> DOWN : open an Incident (started at the FIRST failure), send DOWN alert
 *   DOWN       + 1 success           -> UP   : close the Incident, send RECOVERED alert
 * Alerts fire only on a state change, so a long outage sends one alert, not one per check.
 */
@Service
@RequiredArgsConstructor
public class MonitorStateService {

    /** Everything the checker needs, copied out of the entity so no database session is held during the HTTP call. */
    public record DueCheck(Long id, String url, int timeoutMs, Integer expectedStatus, String keyword) {}

    private final MonitorRepository monitors;
    private final CheckResultRepository results;
    private final IncidentRepository incidents;
    private final AlertService alerts;

    @Value("${app.monitor.failure-threshold}")
    private int failureThreshold;

    /**
     * Finds monitors whose nextCheckAt has passed and immediately schedules their next check.
     * Doing this up front means a slow check can never cause the same monitor to be picked twice.
     */
    @Transactional
    public List<DueCheck> claimDue() {
        Instant now = Instant.now();
        List<DueCheck> due = new ArrayList<>();
        for (Monitor m : monitors.findByActiveTrueAndNextCheckAtLessThanEqual(now)) {
            m.setNextCheckAt(now.plusSeconds(m.getIntervalSec()));
            due.add(new DueCheck(m.getId(), m.getUrl(), m.getTimeoutMs(), m.getExpectedStatus(), m.getKeyword()));
        }
        return due;
    }

    @Transactional
    public void record(Long monitorId, CheckOutcome outcome) {
        Monitor m = monitors.findById(monitorId).orElse(null);
        if (m == null) return; // deleted while the check was running
        Instant now = Instant.now();

        results.save(CheckResult.builder()
                .monitorId(monitorId)
                .success(outcome.success())
                .statusCode(outcome.statusCode())
                .responseMs(outcome.responseMs())
                .error(outcome.error())
                .checkedAt(now)
                .build());

        m.setLastCheckedAt(now);
        m.setLastResponseMs(outcome.responseMs());
        m.setLastError(outcome.success() ? null : outcome.error());

        if (outcome.success()) {
            onSuccess(m, now);
        } else {
            onFailure(m, outcome, now);
        }
    }

    private void onSuccess(Monitor m, Instant now) {
        m.setConsecutiveFailures(0);
        m.setFailingSince(null);
        if (m.getState() == MonitorState.DOWN) {
            long downSeconds = incidents.findFirstByMonitorIdAndResolvedAtIsNullOrderByStartedAtDesc(m.getId())
                    .map(i -> {
                        i.setResolvedAt(now);
                        return Duration.between(i.getStartedAt(), now).getSeconds();
                    }).orElse(0L);
            alerts.sendAlert(new AlertEvent(m.getUserId(), m.getName(), m.getUrl(), false,
                    "Back up after " + Formats.duration(downSeconds)));
        }
        m.setState(MonitorState.UP);
    }

    private void onFailure(Monitor m, CheckOutcome outcome, Instant now) {
        if (m.getConsecutiveFailures() == 0) {
            m.setFailingSince(now);
        }
        m.setConsecutiveFailures(m.getConsecutiveFailures() + 1);

        if (m.getState() != MonitorState.DOWN && m.getConsecutiveFailures() >= failureThreshold) {
            m.setState(MonitorState.DOWN);
            incidents.save(Incident.builder()
                    .monitorId(m.getId())
                    .startedAt(m.getFailingSince())
                    .reason(outcome.error())
                    .build());
            alerts.sendAlert(new AlertEvent(m.getUserId(), m.getName(), m.getUrl(), true, outcome.error()));
        }
    }
}
