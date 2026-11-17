package com.nutritrack.backend.service;

import com.nutritrack.backend.dto.AiHealthAnalysisResponse;
import com.nutritrack.backend.model.FoodProduct;
import com.nutritrack.backend.model.HealthAnalysis;
import com.nutritrack.backend.repository.FoodProductRepository;
import com.nutritrack.backend.repository.HealthAnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Service for AI-powered health analysis (BR-03, BR-09).
 *
 * <p>Flow:
 * <ol>
 *   <li>Check the {@code health_analyses} cache – return immediately on hit.</li>
 *   <li>Load the {@link FoodProduct} from the database (404 if missing).</li>
 *   <li>Compute a deterministic score via {@link HealthScoreCalculator} (never by AI).</li>
 *   <li>Ask Gemini for a plain-language explanation + one meal pairing suggestion.</li>
 *   <li>On Gemini failure / missing API key, use a safe fallback text.</li>
 *   <li>Always set the disclaimer in Java – never rely on the AI for it (BR-09).</li>
 *   <li>Persist the result so Gemini is never called twice for the same barcode.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private static final String DISCLAIMER =
            "This information is purely informational and does not replace a medical consultation.";

    private final HealthAnalysisRepository healthAnalysisRepository;
    private final FoodProductRepository foodProductRepository;
    private final HealthScoreCalculator calculator;
    private final RestClient restClient;

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String geminiModel;

    // ── Public API ────────────────────────────────────────────────────────────

    public AiHealthAnalysisResponse analyse(String barcode) {
        // 1. Cache hit?
        return healthAnalysisRepository.findById(barcode)
                .map(this::toDto)
                .orElseGet(() -> computeAndCache(barcode));
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private AiHealthAnalysisResponse computeAndCache(String barcode) {
        // 2. Load product
        FoodProduct food = foodProductRepository.findById(barcode)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Product not found: " + barcode));

        // 3. Deterministic score
        HealthScoreCalculator.Result result =
                calculator.compute(food.getPer100g(), food.getIngredients());

        // 4. Gemini explanation + pairing (with fallback)
        GeminiText geminiText = callGemini(food, result);

        // 5. Persist
        HealthAnalysis entity = new HealthAnalysis();
        entity.setBarcode(barcode);
        entity.setScore(result.score());
        entity.setRating(result.rating());
        entity.setExplanation(geminiText.explanation());
        entity.setPairingSuggestion(geminiText.pairingSuggestion());
        entity.setDisclaimer(DISCLAIMER);
        entity.setGeneratedAt(Instant.now());
        healthAnalysisRepository.save(entity);

        return toDto(entity);
    }

    private AiHealthAnalysisResponse toDto(HealthAnalysis h) {
        return new AiHealthAnalysisResponse(
                h.getScore(),
                h.getRating(),
                h.getExplanation(),
                h.getPairingSuggestion(),
                DISCLAIMER,          // always from Java, never stored AI value (BR-09)
                h.getGeneratedAt());
    }

    // ── Gemini REST call ──────────────────────────────────────────────────────

    private record GeminiText(String explanation, String pairingSuggestion) {}

    private GeminiText callGemini(FoodProduct food, HealthScoreCalculator.Result result) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            log.warn("GEMINI_API_KEY is not set – using fallback analysis text.");
            return fallback(result);
        }

        String prompt = buildPrompt(food, result);
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + geminiModel + ":generateContent?key=" + geminiApiKey;

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "contents", List.of(
                                    Map.of("parts", List.of(
                                            Map.of("text", prompt))))))
                    .retrieve()
                    .body(Map.class);

            String raw = extractText(body);
            return parseGeminiResponse(raw);

        } catch (Exception ex) {
            log.error("Gemini call failed for barcode {}: {} – using fallback.",
                    food.getBarcode(), ex.getMessage());
            return fallback(result);
        }
    }

    private String buildPrompt(FoodProduct food, HealthScoreCalculator.Result result) {
        var n = food.getPer100g();
        return String.format(
                """
                You are a registered dietitian writing a brief nutrition note for a consumer app.
                
                Product: %s
                Health score (computed): %d/100  Rating: %s
                Per 100 g – Calories: %.1f kcal | Protein: %.1f g | Carbs: %.1f g | Fat: %.1f g
                Ingredients: %s
                
                Write exactly two paragraphs separated by the delimiter "|||":
                1. A 2-3 sentence plain-language explanation of this product's nutritional strengths \
                and weaknesses (based on the data above, do NOT invent numbers).
                2. ONE concrete meal-pairing suggestion (one sentence).
                
                Do NOT include any health disclaimer – that is handled separately.
                Do NOT repeat the numbers verbatim. Be concise and factual.
                """,
                food.getName(),
                result.score(), result.rating(),
                n.getCalories(), n.getProteinG(), n.getCarbsG(), n.getFatG(),
                food.getIngredients() != null ? food.getIngredients() : "not available"
        );
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> body) {
        try {
            var candidates = (List<Map<String, Object>>) body.get("candidates");
            var content    = (Map<String, Object>) candidates.get(0).get("content");
            var parts      = (List<Map<String, Object>>) content.get("parts");
            return (String) parts.get(0).get("text");
        } catch (Exception e) {
            throw new RuntimeException("Unexpected Gemini response structure", e);
        }
    }

    private GeminiText parseGeminiResponse(String raw) {
        if (raw == null || raw.isBlank()) return fallbackText();
        String[] parts = raw.split("\\|\\|\\|", 2);
        String explanation      = parts[0].trim();
        String pairingSuggestion = parts.length > 1 ? parts[1].trim() : "Enjoy as part of a balanced meal.";
        return new GeminiText(explanation, pairingSuggestion);
    }

    private GeminiText fallback(HealthScoreCalculator.Result result) {
        return new GeminiText(
                "This product received a " + result.rating().toLowerCase()
                        + " health score of " + result.score()
                        + "/100 based on its calorie, protein, fat, and carbohydrate content. "
                        + "Consider it in the context of your overall daily diet.",
                "Pair with fresh vegetables or a source of lean protein to create a balanced meal."
        );
    }

    private GeminiText fallbackText() {
        return new GeminiText(
                "Nutritional analysis is currently unavailable. Please review the product label.",
                "Pair with fresh vegetables or a source of lean protein to create a balanced meal."
        );
    }
}
