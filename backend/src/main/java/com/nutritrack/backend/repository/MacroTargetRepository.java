package com.nutritrack.backend.repository;

import com.nutritrack.backend.model.MacroTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface MacroTargetRepository extends JpaRepository<MacroTarget, UUID> {
    Optional<MacroTarget> findByUserId(UUID userId);
}