package com.example.pulse.repository;

import com.example.pulse.entity.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Optional<Incident> findFirstByMonitorIdAndResolvedAtIsNullOrderByStartedAtDesc(Long monitorId);

    List<Incident> findTop50ByMonitorIdOrderByStartedAtDesc(Long monitorId);

    List<Incident> findTop10ByMonitorIdInOrderByStartedAtDesc(Collection<Long> monitorIds);

    /** Incidents that overlap the window [from, to]. */
    @Query("select i from Incident i where i.monitorId = :monitorId and i.startedAt < :to "
            + "and (i.resolvedAt is null or i.resolvedAt > :from)")
    List<Incident> findOverlapping(@Param("monitorId") Long monitorId,
                                   @Param("from") Instant from, @Param("to") Instant to);

    @Modifying
    @Query("delete from Incident i where i.monitorId = :monitorId")
    void deleteAllForMonitor(@Param("monitorId") Long monitorId);
}
