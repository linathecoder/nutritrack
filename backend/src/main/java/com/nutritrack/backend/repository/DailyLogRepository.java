package com.nutritrack.backend.repository;

import com.nutritrack.backend.model.DailyLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyLogRepository extends JpaRepository<DailyLog, UUID> {
    Optional<DailyLog> findByUserIdAndDate(UUID userId, LocalDate date);

    /** Logged days of a user between two dates (inclusive), oldest first. Used by the Analysis page. */
    List<DailyLog> findByUserIdAndDateBetweenOrderByDateAsc(UUID userId, LocalDate from, LocalDate to);
}
