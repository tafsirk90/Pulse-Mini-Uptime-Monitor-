package com.example.pulse.scheduler;

import com.example.pulse.repository.CheckResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** Check results pile up fast, so old ones are deleted nightly. Incidents are kept forever. */
@Component
@RequiredArgsConstructor
@Slf4j
public class RetentionJob {

    private final CheckResultRepository results;

    @Value("${app.monitor.retention-days}")
    private int retentionDays;

    @Scheduled(cron = "0 30 3 * * *")   // every day at 03:30
    @Transactional
    public void purgeOldResults() {
        int deleted = results.purgeOlderThan(Instant.now().minus(Duration.ofDays(retentionDays)));
        log.info("RetentionJob: deleted {} check result(s) older than {} days", deleted, retentionDays);
    }
}
