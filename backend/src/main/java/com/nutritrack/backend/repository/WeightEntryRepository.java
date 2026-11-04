package com.nutritrack.backend.repository;

import com.nutritrack.backend.model.WeightEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WeightEntryRepository extends JpaRepository<WeightEntry, UUID> {
    List<WeightEntry> findByUserIdOrderByDateAsc(UUID userId);
}