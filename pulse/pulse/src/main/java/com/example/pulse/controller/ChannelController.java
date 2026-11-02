package com.example.pulse.controller;

import com.example.pulse.dto.Dtos.ChannelRequest;
import com.example.pulse.dto.Dtos.ChannelResponse;
import com.example.pulse.dto.Dtos.MessageResponse;
import com.example.pulse.security.CurrentUser;
import com.example.pulse.service.ChannelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/channels")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService service;
    private final CurrentUser currentUser;

    @GetMapping
    public List<ChannelResponse> list(Authentication auth) {
        return service.list(currentUser.id(auth));
    }

    @PostMapping
    public ResponseEntity<ChannelResponse> create(Authentication auth, @Valid @RequestBody ChannelRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(currentUser.id(auth), body));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable Long id) {
        service.delete(currentUser.id(auth), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    public MessageResponse test(Authentication auth, @PathVariable Long id) {
        return service.test(currentUser.id(auth), id);
    }
}
