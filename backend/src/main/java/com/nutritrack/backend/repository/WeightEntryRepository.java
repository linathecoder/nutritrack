package com.nutritrack.backend.repository;

import com.nutritrack.backend.model.WeightEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WeightEntryRepository extends JpaRepository<WeightEntry, UUID> {
    List<WeightEntry> findByUserIdOrderByDateAsc(UUID userId);

    List<WeightEntry> findByUserIdAndDateGreaterThanEqualOrderByDateAsc(UUID userId, LocalDate from);

    Optional<WeightEntry> findByUserIdAndDate(UUID userId, LocalDate date);

    Optional<WeightEntry> findFirstByUserIdOrderByDateDesc(UUID userId);

    boolean existsByUserId(UUID userId);
}