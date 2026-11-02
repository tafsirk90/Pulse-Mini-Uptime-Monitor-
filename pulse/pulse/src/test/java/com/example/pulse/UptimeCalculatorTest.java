package com.example.pulse;

import com.example.pulse.util.UptimeCalculator;
import com.example.pulse.util.UptimeCalculator.Span;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UptimeCalculatorTest {

    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");

    private static Instant minute(long m) {
        return BASE.plusSeconds(m * 60);
    }

    @Test
    void noIncidentsMeansFullUptime() {
        assertEquals(100.0, UptimeCalculator.uptimePercent(0, minute(0), minute(100)));
    }

    @Test
    void incidentInsideWindowCountsFully() {
        long down = UptimeCalculator.downtimeSeconds(List.of(new Span(minute(10), minute(20))), minute(0), minute(100));
        assertEquals(600, down);
        assertEquals(90.0, UptimeCalculator.uptimePercent(down, minute(0), minute(100)));
    }

    @Test
    void incidentStartedBeforeWindowIsClipped() {
        long down = UptimeCalculator.downtimeSeconds(List.of(new Span(minute(-10), minute(10))), minute(0), minute(100));
        assertEquals(600, down);
    }

    @Test
    void ongoingIncidentRunsToEndOfWindow() {
        long down = UptimeCalculator.downtimeSeconds(List.of(new Span(minute(90), null)), minute(0), minute(100));
        assertEquals(600, down);
    }

    @Test
    void incidentOutsideWindowIsIgnored() {
        long down = UptimeCalculator.downtimeSeconds(List.of(new Span(minute(200), minute(210))), minute(0), minute(100));
        assertEquals(0, down);
    }

    @Test
    void emptyWindowCountsAsFullUptime() {
        assertEquals(100.0, UptimeCalculator.uptimePercent(0, minute(5), minute(5)));
    }

    @Test
    void percentileUsesNearestRank() {
        List<Long> values = List.of(10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L, 90L, 100L);
        assertEquals(100L, UptimeCalculator.percentile(values, 95));
        assertEquals(50L, UptimeCalculator.percentile(values, 50));
        assertNull(UptimeCalculator.percentile(List.of(), 95));
    }
}
