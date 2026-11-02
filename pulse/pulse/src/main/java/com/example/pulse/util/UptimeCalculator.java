package com.example.pulse.util;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Pure functions for uptime maths (easy to unit test).
 *
 * Uptime is based on incidents, not on counting checks, so the answer does not depend on how
 * often a monitor is checked:  uptime % = (window - downtime) / window * 100
 */
public final class UptimeCalculator {
    private UptimeCalculator() {}

    /** One outage. An end of null means the outage is still ongoing. */
    public record Span(Instant start, Instant end) {}

    /** Total seconds of downtime inside [from, to]. Each outage is clipped to the window. */
    public static long downtimeSeconds(List<Span> spans, Instant from, Instant to) {
        long total = 0;
        for (Span s : spans) {
            Instant start = s.start().isBefore(from) ? from : s.start();
            Instant end = (s.end() == null || s.end().isAfter(to)) ? to : s.end();
            if (end.isAfter(start)) {
                total += Duration.between(start, end).getSeconds();
            }
        }
        return total;
    }

    /** Uptime percentage rounded to 2 decimals. An empty window counts as 100%. */
    public static double uptimePercent(long downtimeSeconds, Instant from, Instant to) {
        long window = Duration.between(from, to).getSeconds();
        if (window <= 0) return 100.0;
        double pct = (window - Math.min(downtimeSeconds, window)) * 100.0 / window;
        return Math.round(pct * 100.0) / 100.0;
    }

    /** Nearest-rank percentile of an already sorted list (e.g. p = 95). Null for an empty list. */
    public static Long percentile(List<Long> sorted, double p) {
        if (sorted.isEmpty()) return null;
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        idx = Math.max(0, Math.min(sorted.size() - 1, idx));
        return sorted.get(idx);
    }
}
