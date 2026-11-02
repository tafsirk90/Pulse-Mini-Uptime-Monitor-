package com.example.pulse.repository;

import com.example.pulse.entity.CheckResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CheckResultRepository extends JpaRepository<CheckResult, Long> {

    List<CheckResult> findByMonitorIdAndCheckedAtGreaterThanEqualOrderByCheckedAtAsc(Long monitorId, Instant from);

    List<CheckResult> findTop30ByMonitorIdOrderByCheckedAtDesc(Long monitorId);

    @Modifying
    @Query("delete from CheckResult c where c.checkedAt < :cutoff")
    int purgeOlderThan(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("delete from CheckResult c where c.monitorId = :monitorId")
    void deleteAllForMonitor(@Param("monitorId") Long monitorId);
}
