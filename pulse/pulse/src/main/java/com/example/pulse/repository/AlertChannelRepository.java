package com.example.pulse.repository;

import com.example.pulse.entity.AlertChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertChannelRepository extends JpaRepository<AlertChannel, Long> {
    List<AlertChannel> findByUserIdOrderByIdAsc(Long userId);
    Optional<AlertChannel> findByIdAndUserId(Long id, Long userId);
    long countByUserId(Long userId);
}
