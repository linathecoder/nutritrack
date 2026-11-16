package com.nutritrack.backend.repository;

import com.nutritrack.backend.model.MacroTargetHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MacroTargetHistoryRepository extends JpaRepository<MacroTargetHistory, UUID> {

    Optional<MacroTargetHistory> findByUserIdAndDate(UUID userId, LocalDate date);

    boolean existsByUserId(UUID userId);

    Optional<MacroTargetHistory> findFirstByUserIdAndDateLessThanEqualOrderByDateDesc(UUID userId, LocalDate date);

    List<MacroTargetHistory> findByUserIdAndDateGreaterThanEqualOrderByDateAsc(UUID userId, LocalDate from);
}
