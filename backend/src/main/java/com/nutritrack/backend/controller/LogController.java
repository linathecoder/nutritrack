package com.nutritrack.backend.controller;

import com.nutritrack.backend.dto.DailyLogResponse;
import com.nutritrack.backend.dto.LogFoodRequest;
import com.nutritrack.backend.service.LogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    /**
     * GET /api/logs/today – existing endpoint, unchanged (backward-compatible).
     */
    @GetMapping("/today")
    public ResponseEntity<DailyLogResponse> getToday(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(logService.getToday(userId));
    }

    /**
     * GET /api/logs?date=YYYY-MM-DD – Task 2: daily log by date.
     * If the {@code date} param is omitted, defaults to today in Africa/Tunis.
     * Response shape is identical to GET /api/logs/today.
     */
    @GetMapping
    public ResponseEntity<DailyLogResponse> getByDate(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(logService.getByDate(userId, date));
    }

    @PostMapping
    public ResponseEntity<DailyLogResponse.Entry> addEntry(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody LogFoodRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(logService.addEntry(userId, request));
    }

    /**
     * PUT /api/logs/{id} – Task 3: now supports quantity updates for ALL food types.
     * Quantity must be > 0 (BR-07); nutrients recompute proportionally (BR-02).
     */
    @PutMapping("/{id}")
    public ResponseEntity<DailyLogResponse.Entry> updateEntry(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody LogFoodRequest request) {
        return ResponseEntity.ok(logService.updateEntry(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEntry(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        logService.deleteEntry(userId, id);
        return ResponseEntity.noContent().build();
    }
}
