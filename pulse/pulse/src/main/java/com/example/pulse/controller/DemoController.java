package com.example.pulse.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A fake website for demos. It is "up" for 60 seconds, then "down" (HTTP 503) for 60 seconds, repeating.
 * Add http://localhost:8080/demo/flaky as a monitor to watch an outage open and close live.
 */
@RestController
@RequestMapping("/demo")
public class DemoController {

    @GetMapping("/flaky")
    public ResponseEntity<String> flaky() throws InterruptedException {
        Thread.sleep(ThreadLocalRandom.current().nextInt(40, 350));   // makes the response-time chart interesting
        long secondInCycle = Instant.now().getEpochSecond() % 120;
        if (secondInCycle >= 60) {
            return ResponseEntity.status(503).body("Simulated outage");
        }
        return ResponseEntity.ok("Pulse demo site is healthy");
    }
}
