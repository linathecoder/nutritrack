package com.nutritrack.backend.controller;

import com.nutritrack.backend.dto.DailyLogResponse;
import com.nutritrack.backend.dto.LogFoodRequest;
import com.nutritrack.backend.service.LogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    @GetMapping("/today")
    public ResponseEntity<DailyLogResponse> getToday(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(logService.getToday(userId));
    }

    @PostMapping
    public ResponseEntity<DailyLogResponse.Entry> addEntry(@AuthenticationPrincipal UUID userId,
                                                           @Valid @RequestBody LogFoodRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(logService.addEntry(userId, request));
    }
}
