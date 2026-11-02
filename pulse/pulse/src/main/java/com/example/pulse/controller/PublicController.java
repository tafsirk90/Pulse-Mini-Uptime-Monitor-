package com.example.pulse.controller;

import com.example.pulse.dto.Dtos.PublicStatus;
import com.example.pulse.service.PublicStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final PublicStatusService service;

    @GetMapping("/status/{slug}")
    public PublicStatus status(@PathVariable String slug) {
        return service.status(slug);
    }
}
