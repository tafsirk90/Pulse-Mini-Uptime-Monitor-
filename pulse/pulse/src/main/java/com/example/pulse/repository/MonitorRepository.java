package com.example.pulse.repository;

import com.example.pulse.entity.Monitor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MonitorRepository extends JpaRepository<Monitor, Long> {
    List<Monitor> findByUserIdOrderByCreatedAtAsc(Long userId);
    Optional<Monitor> findByIdAndUserId(Long id, Long userId);
    long countByUserId(Long userId);
    List<Monitor> findByActiveTrueAndNextCheckAtLessThanEqual(Instant now);
}
