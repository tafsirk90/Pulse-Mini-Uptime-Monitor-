package com.example.pulse.service;

import com.example.pulse.dto.Dtos.Point;
import com.example.pulse.dto.Dtos.StatsResponse;
import com.example.pulse.entity.CheckResult;
import com.example.pulse.entity.Monitor;
import com.example.pulse.exception.BadRequestException;
import com.example.pulse.repository.CheckResultRepository;
import com.example.pulse.repository.IncidentRepository;
import com.example.pulse.util.UptimeCalculator;
import com.example.pulse.util.UptimeCalculator.Span;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatsService {

    private static final int MAX_POINTS = 90;

    private final CheckResultRepository results;
    private final IncidentRepository incidents;

    /** Uptime over the last `window`, never counting time before the monitor existed. */
    @Transactional(readOnly = true)
    public double uptimePercent(Monitor m, Duration window) {
        Instant now = Instant.now();
        Instant from = effectiveFrom(m, now, window);
        return UptimeCalculator.uptimePercent(downtimeSeconds(m.getId(), from, now), from, now);
    }

    /** The last 30 check outcomes, oldest first (true = success). Used for the heartbeat strip. */
    @Transactional(readOnly = true)
    public List<Boolean> recentChecks(Long monitorId) {
        List<Boolean> list = new ArrayList<>();
        for (CheckResult r : results.findTop30ByMonitorIdOrderByCheckedAtDesc(monitorId)) {
            list.add(r.isSuccess());
        }
        Collections.reverse(list);
        return list;
    }

    @Transactional(readOnly = true)
    public StatsResponse stats(Monitor m, String range) {
        Duration window = parseRange(range);
        Instant now = Instant.now();
        Instant from = effectiveFrom(m, now, window);

        List<CheckResult> list = results.findByMonitorIdAndCheckedAtGreaterThanEqualOrderByCheckedAtAsc(m.getId(), from);
        long failed = list.stream().filter(r -> !r.isSuccess()).count();
        List<Long> times = list.stream().filter(CheckResult::isSuccess)
                .map(CheckResult::getResponseMs).sorted().toList();
        Long avg = times.isEmpty() ? null : Math.round(times.stream().mapToLong(Long::longValue).average().orElse(0));
        long downtime = downtimeSeconds(m.getId(), from, now);

        return new StatsResponse(range, UptimeCalculator.uptimePercent(downtime, from, now), avg,
                UptimeCalculator.percentile(times, 95), list.size(), failed, downtime,
                downsample(list, from, now));
    }

    /** Groups raw checks into at most MAX_POINTS time buckets so the chart stays light for 30-day ranges. */
    private List<Point> downsample(List<CheckResult> list, Instant from, Instant to) {
        int n = MAX_POINTS;
        long fromMs = from.toEpochMilli();
        long span = Math.max(1, to.toEpochMilli() - fromMs);
        long[] sum = new long[n];
        int[] okCount = new int[n];
        boolean[] failedAny = new boolean[n];
        boolean[] has = new boolean[n];

        for (CheckResult r : list) {
            int idx = (int) Math.max(0, Math.min(n - 1, (r.getCheckedAt().toEpochMilli() - fromMs) * n / span));
            has[idx] = true;
            if (r.isSuccess()) {
                sum[idx] += r.getResponseMs();
                okCount[idx]++;
            } else {
                failedAny[idx] = true;
            }
        }
        List<Point> points = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!has[i]) continue;
            long t = fromMs + span * (2L * i + 1) / (2L * n);
            Long ms = okCount[i] > 0 ? sum[i] / okCount[i] : null;
            points.add(new Point(t, ms, failedAny[i]));
        }
        return points;
    }

    private long downtimeSeconds(Long monitorId, Instant from, Instant to) {
        List<Span> spans = incidents.findOverlapping(monitorId, from, to).stream()
                .map(i -> new Span(i.getStartedAt(), i.getResolvedAt())).toList();
        return UptimeCalculator.downtimeSeconds(spans, from, to);
    }

    private Instant effectiveFrom(Monitor m, Instant now, Duration window) {
        Instant from = now.minus(window);
        return m.getCreatedAt().isAfter(from) ? m.getCreatedAt() : from;
    }

    private Duration parseRange(String range) {
        return switch (range) {
            case "24h" -> Duration.ofHours(24);
            case "7d" -> Duration.ofDays(7);
            case "30d" -> Duration.ofDays(30);
            default -> throw new BadRequestException("range must be 24h, 7d or 30d");
        };
    }
}
