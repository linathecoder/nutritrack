package com.nutritrack.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/**
 * Response DTO for GET /api/analysis/ai?barcode={barcode}.
 */
@Getter
@AllArgsConstructor
public class AiHealthAnalysisResponse {

    /** Deterministic score 0-100 (computed in Java, never by AI). */
    private int score;

    /** Human-readable rating: Excellent / Good / Fair / Poor. */
    private String rating;

    /** Short plain-language explanation (Gemini or fallback). */
    private String explanation;

    /** One meal pairing suggestion (Gemini or fallback). */
    private String pairingSuggestion;

    /**
     * Mandatory disclaimer – always set by backend, never by AI (BR-09).
     * Value: "This information is purely informational and does not replace a medical consultation."
     */
    private String disclaimer;

    /** UTC timestamp of when this analysis was generated. */
    private Instant generatedAt;
}
