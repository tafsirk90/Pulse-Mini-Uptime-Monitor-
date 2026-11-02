package com.example.pulse.controller;

import com.example.pulse.dto.Dtos.IncidentResponse;
import com.example.pulse.dto.Dtos.MonitorRequest;
import com.example.pulse.dto.Dtos.MonitorResponse;
import com.example.pulse.dto.Dtos.StatsResponse;
import com.example.pulse.security.CurrentUser;
import com.example.pulse.service.MonitorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/monitors")
@RequiredArgsConstructor
public class MonitorController {

    private final MonitorService service;
    private final CurrentUser currentUser;

    @GetMapping
    public List<MonitorResponse> list(Authentication auth) {
        return service.list(currentUser.id(auth));
    }

    @PostMapping
    public ResponseEntity<MonitorResponse> create(Authentication auth, @Valid @RequestBody MonitorRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(currentUser.id(auth), body));
    }

    @PutMapping("/{id}")
    public MonitorResponse update(Authentication auth, @PathVariable Long id, @Valid @RequestBody MonitorRequest body) {
        return service.update(currentUser.id(auth), id, body);
    }

    /** PUT /api/monitors/5/active?value=false pauses, value=true resumes. */
    @PutMapping("/{id}/active")
    public MonitorResponse setActive(Authentication auth, @PathVariable Long id, @RequestParam boolean value) {
        return service.setActive(currentUser.id(auth), id, value);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable Long id) {
        service.delete(currentUser.id(auth), id);
        return ResponseEntity.noContent().build();
    }

    /** GET /api/monitors/5/stats?range=24h|7d|30d */
    @GetMapping("/{id}/stats")
    public StatsResponse stats(Authentication auth, @PathVariable Long id,
                               @RequestParam(defaultValue = "24h") String range) {
        return service.stats(currentUser.id(auth), id, range);
    }

    @GetMapping("/{id}/incidents")
    public List<IncidentResponse> incidents(Authentication auth, @PathVariable Long id) {
        return service.incidents(currentUser.id(auth), id);
    }
}
