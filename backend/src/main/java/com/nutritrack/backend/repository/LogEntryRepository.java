package com.nutritrack.backend.repository;

import com.nutritrack.backend.model.LogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LogEntryRepository extends JpaRepository<LogEntry, UUID> {
    Optional<LogEntry> findByIdAndDailyLogUserId(UUID id, UUID userId);
}
