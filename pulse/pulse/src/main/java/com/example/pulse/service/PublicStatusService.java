package com.example.pulse.service;

import com.example.pulse.dto.Dtos.PublicIncident;
import com.example.pulse.dto.Dtos.PublicMonitor;
import com.example.pulse.dto.Dtos.PublicStatus;
import com.example.pulse.entity.Monitor;
import com.example.pulse.entity.User;
import com.example.pulse.exception.ResourceNotFoundException;
import com.example.pulse.repository.IncidentRepository;
import com.example.pulse.repository.MonitorRepository;
import com.example.pulse.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PublicStatusService {

    private final UserRepository users;
    private final MonitorRepository monitors;
    private final IncidentRepository incidents;
    private final StatsService stats;

    /** Public data for /status/{slug}. Monitor URLs are deliberately not included. */
    @Transactional(readOnly = true)
    public PublicStatus status(String slug) {
        User user = users.findBySlug(slug).orElseThrow(() -> new ResourceNotFoundException("Status page not found"));
        List<Monitor> list = monitors.findByUserIdOrderByCreatedAtAsc(user.getId());

        List<PublicMonitor> out = list.stream().map(m -> new PublicMonitor(
                m.getName(), m.displayStatus(),
                stats.uptimePercent(m, Duration.ofHours(24)),
                stats.uptimePercent(m, Duration.ofDays(30)),
                stats.recentChecks(m.getId()))).toList();

        Map<Long, String> names = list.stream().collect(Collectors.toMap(Monitor::getId, Monitor::getName));
        List<PublicIncident> recent = List.of();
        if (!names.isEmpty()) {
            recent = incidents.findTop10ByMonitorIdInOrderByStartedAtDesc(names.keySet()).stream()
                    .map(i -> new PublicIncident(names.get(i.getMonitorId()), i.getStartedAt(), i.getResolvedAt(),
                            i.getResolvedAt() == null, i.getReason()))
                    .toList();
        }

        int up = (int) out.stream().filter(m -> m.status().equals("UP")).count();
        int down = (int) out.stream().filter(m -> m.status().equals("DOWN")).count();
        return new PublicStatus(user.getName(), out.size(), up, down, out, recent);
    }
}
