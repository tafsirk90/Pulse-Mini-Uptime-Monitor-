package com.example.pulse.service;

import com.example.pulse.entity.AlertChannel;
import com.example.pulse.net.UrlGuard;
import com.example.pulse.repository.AlertChannelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final AlertChannelRepository channels;
    private final EmailService emailService;
    private final UrlGuard guard;
    private final ObjectMapper mapper;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** Called on DOWN and RECOVERED. Runs on its own thread pool so checks are never delayed. */
    @Async("alertExecutor")
    public void sendAlert(AlertEvent event) {

        String subject = (event.down() ? "[DOWN] " : "[RECOVERED] ")
                + event.monitorName()
                + " at " + java.time.LocalTime.now().withNano(0);

        String text = subject + " (" + event.url() + "): " + event.detail();

        List<AlertChannel> userChannels =
                channels.findByUserIdOrderByIdAsc(event.userId());

        log.info("ALERT: sending '{}' to {} channel(s)",
                subject, userChannels.size());

        for (AlertChannel channel : userChannels) {
            try {
                String result = deliver(channel, subject, text);

                log.info("ALERT: channel {} ({}): {}",
                        channel.getId(),
                        channel.getType(),
                        result);

            } catch (Exception e) {
                log.error("ALERT: delivery FAILED for channel {}: {}",
                        channel.getId(),
                        e.getMessage(), e);
            }
        }
    }

    /** Delivers one message to one channel. Returns a short note for the user. Throws if delivery fails. */
    public String deliver(AlertChannel channel, String subject, String text) throws Exception {
        switch (channel.getType()) {
            case EMAIL -> {
                boolean sent = emailService.send(channel.getTarget(), subject, text);
                return sent ? "Email sent."
                        : "Email is not configured on the server, so the message was only written to the log.";
            }
            case WEBHOOK -> {
                postWebhook(channel.getTarget(), text);
                return "Webhook delivered.";
            }
            default -> throw new IllegalStateException("Unknown channel type");
        }
    }

    private void postWebhook(String target, String text) throws Exception {
        URI uri = guard.requirePublic(target);
        // "content" is what Discord reads, "text" is what Slack reads; each service ignores the other.
        String json = mapper.writeValueAsString(Map.of("content", text, "text", text));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Webhook returned HTTP " + response.statusCode());
        }
    }
}
