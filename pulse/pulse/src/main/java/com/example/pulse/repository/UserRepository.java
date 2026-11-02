package com.example.pulse.repository;

import com.example.pulse.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findBySlug(String slug);
    boolean existsByEmail(String email);
    boolean existsBySlug(String slug);
}
