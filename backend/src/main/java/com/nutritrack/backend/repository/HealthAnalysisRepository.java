package com.nutritrack.backend.repository;

import com.nutritrack.backend.model.HealthAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthAnalysisRepository extends JpaRepository<HealthAnalysis, String> {
}
