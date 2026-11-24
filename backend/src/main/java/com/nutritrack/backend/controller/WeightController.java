package com.nutritrack.backend.controller;

import com.nutritrack.backend.dto.WeightHistoryResponse;
import com.nutritrack.backend.dto.WeightRequest;
import com.nutritrack.backend.dto.WeightResponse;
import com.nutritrack.backend.service.WeightService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/weight")
@RequiredArgsConstructor
public class WeightController {

    private final WeightService weightService;

    @PostMapping
    public ResponseEntity<WeightResponse> log(@AuthenticationPrincipal UUID userId,
                                              @Valid @RequestBody WeightRequest request) {
        return ResponseEntity.ok(weightService.log(userId, request));
    }

    @PutMapping("/{date}")
    public ResponseEntity<WeightResponse> update(@AuthenticationPrincipal UUID userId,
                                                 @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                 @Valid @RequestBody WeightRequest request) {
        return ResponseEntity.ok(weightService.update(userId, date, request));
    }

    @DeleteMapping("/{date}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UUID userId,
                                       @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        weightService.delete(userId, date);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<WeightHistoryResponse> history(@AuthenticationPrincipal UUID userId,
                                                         @RequestParam(defaultValue = "90") int days) {
        return ResponseEntity.ok(weightService.history(userId, days));
    }
}
