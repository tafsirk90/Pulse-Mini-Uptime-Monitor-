package com.example.pulse.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the same static page for every /status/{slug}; the page reads the slug from its own URL. */
@Controller
public class StatusPageController {

    @GetMapping("/status/{slug}")
    public String page() {
        return "forward:/status.html";
    }
}
