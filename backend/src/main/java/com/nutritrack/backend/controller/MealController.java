package com.nutritrack.backend.controller;

import com.nutritrack.backend.dto.SavedMealResponse;
import com.nutritrack.backend.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** "My Meals" page and the Log Meal search. */
@RestController
@RequestMapping("/api/meals")
@RequiredArgsConstructor
public class MealController {

    private final LogService logService;

    /** GET /api/meals : meals created by the current user, most recently used first. */
    @GetMapping
    public ResponseEntity<List<SavedMealResponse>> list(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(logService.savedMeals(userId));
    }

    /**
     * GET /api/meals/history?q=choc : everything the user already logged (own meals, scanned
     * products, foods) whose name or ingredients contain q. Empty q returns the most recent ones.
     */
    @GetMapping("/history")
    public ResponseEntity<List<SavedMealResponse>> history(@AuthenticationPrincipal UUID userId,
                                                            @RequestParam(defaultValue = "") String q) {
        return ResponseEntity.ok(logService.loggedFoods(userId, q));
    }
}
