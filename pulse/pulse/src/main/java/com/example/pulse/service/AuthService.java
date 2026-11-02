package com.example.pulse.service;

import com.example.pulse.dto.Dtos.AuthResponse;
import com.example.pulse.dto.Dtos.LoginRequest;
import com.example.pulse.dto.Dtos.RegisterRequest;
import com.example.pulse.entity.User;
import com.example.pulse.exception.BadRequestException;
import com.example.pulse.repository.UserRepository;
import com.example.pulse.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private final SecureRandom random = new SecureRandom();

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new BadRequestException("Email already registered");
        }
        User user = users.save(User.builder()
                .name(req.name().trim())
                .email(email)
                .password(passwordEncoder.encode(req.password()))
                .slug(uniqueSlug(req.name()))
                .build());
        return toResponse(user);
    }

    public AuthResponse login(LoginRequest req) {
        String email = req.email().trim().toLowerCase();
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, req.password()));
        return toResponse(users.findByEmail(email).orElseThrow());
    }

    private AuthResponse toResponse(User u) {
        return new AuthResponse(jwtService.generateToken(u.getEmail()), u.getName(), u.getEmail(), u.getSlug());
    }

    /** "Jane Doe" -> "jane-doe-k3x9" */
    private String uniqueSlug(String name) {
        String base = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (base.isEmpty()) base = "user";
        if (base.length() > 24) base = base.substring(0, 24);
        String slug;
        do {
            StringBuilder suffix = new StringBuilder();
            for (int i = 0; i < 4; i++) suffix.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            slug = base + "-" + suffix;
        } while (users.existsBySlug(slug));
        return slug;
    }
}
