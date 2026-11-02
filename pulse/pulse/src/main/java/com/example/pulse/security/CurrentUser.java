package com.example.pulse.security;

import com.example.pulse.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Turns the authenticated principal into the database id of the user. */
@Component
@RequiredArgsConstructor
public class CurrentUser {

    private final UserRepository userRepository;

    public Long id(Authentication auth) {
        return userRepository.findByEmail(auth.getName()).orElseThrow().getId();
    }
}
