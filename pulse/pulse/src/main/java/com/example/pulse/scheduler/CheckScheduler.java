package com.example.pulse.scheduler;

import com.example.pulse.net.CheckOutcome;
import com.example.pulse.net.HttpChecker;
import com.example.pulse.service.MonitorStateService;
import com.example.pulse.service.MonitorStateService.DueCheck;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every few seconds: find due monitors and run their checks in parallel on a thread pool.
 * The "running" set makes sure a slow check is never started twice for the same monitor.
 */
@Component
@Slf4j
public class CheckScheduler {

    private final MonitorStateService stateService;
    private final HttpChecker checker;
    private final ThreadPoolTaskExecutor executor;
    private final Set<Long> running = ConcurrentHashMap.newKeySet();

    public CheckScheduler(MonitorStateService stateService, HttpChecker checker,
                          @Qualifier("checkExecutor") ThreadPoolTaskExecutor executor) {
        this.stateService = stateService;
        this.checker = checker;
        this.executor = executor;
    }

    @Scheduled(fixedDelayString = "${app.monitor.tick-ms}", initialDelayString = "${app.monitor.tick-ms}")
    public void tick() {
        try {
            for (DueCheck due : stateService.claimDue()) {
                if (!running.add(due.id())) continue;   // previous check still in progress
                try {
                    executor.execute(() -> runOne(due));
                } catch (TaskRejectedException e) {
                    running.remove(due.id());
                    log.warn("Check queue is full, skipping monitor {}", due.id());
                }
            }
        } catch (Exception e) {
            log.error("Scheduler tick failed", e);
        }
    }

    private void runOne(DueCheck due) {
        try {
            CheckOutcome outcome = checker.check(due.url(), due.timeoutMs(), due.expectedStatus(), due.keyword());
            stateService.record(due.id(), outcome);
        } catch (Exception e) {
            log.error("Check failed unexpectedly for monitor {}", due.id(), e);
        } finally {
            running.remove(due.id());
        }
    }
}
