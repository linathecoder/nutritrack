package com.nutritrack.backend.controller;

import com.nutritrack.backend.dto.AiHealthAnalysisResponse;
import com.nutritrack.backend.service.AnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for health analysis features (BR-03, BR-09).
 *
 * <p>All endpoints require a valid JWT (enforced by SecurityConfig – anyRequest().authenticated()).
 * No changes have been made to the barcode scan flow or OpenFoodFacts integration.
 */
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    /**
     * GET /api/analysis/ai?barcode={barcode}
     *
     * <p>Returns the AI health analysis for the given product barcode.
     * The product must already exist in the database (populated by the scan flow).
     * The deterministic score (0-100) is computed in Java; Gemini only writes the
     * plain-language explanation and meal pairing suggestion.
     * The disclaimer is always set by backend code (BR-09).
     *
     * @param barcode the product barcode (same value used by the scanner)
     * @param userId  injected from the JWT by Spring Security
     */
    @GetMapping("/ai")
    public ResponseEntity<AiHealthAnalysisResponse> getAiAnalysis(
            @RequestParam String barcode,
            @AuthenticationPrincipal UUID userId) {

        return ResponseEntity.ok(analysisService.analyse(barcode));
    }
}
