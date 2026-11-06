package com.nutritrack.backend.controller;

import com.nutritrack.backend.dto.DailyLogResponse;
import com.nutritrack.backend.service.LogService;
import lombok.RequiredArgsConstructor;
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
}
