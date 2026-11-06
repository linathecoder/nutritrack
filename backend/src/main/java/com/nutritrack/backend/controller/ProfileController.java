package com.nutritrack.backend.controller;

import com.nutritrack.backend.dto.ProfileCalculationRequest;
import com.nutritrack.backend.dto.ProfileCalculationResponse;
import com.nutritrack.backend.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProfileController {

    private final ProfileService profileService;

    @PostMapping("/calculate")
    public ResponseEntity<ProfileCalculationResponse> calculatePreview(
            @Valid @RequestBody ProfileCalculationRequest request
    ) {
        ProfileCalculationResponse response = profileService.previewCalculation(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<ProfileCalculationResponse> calculateAndSave(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody ProfileCalculationRequest request
    ) {
        request.setUserId(userId);
        ProfileCalculationResponse response = profileService.calculateAndSaveProfile(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<ProfileCalculationResponse> getMyProfile(@AuthenticationPrincipal UUID userId) {
        ProfileCalculationResponse response = profileService.getProfileByUserId(userId);
        return ResponseEntity.ok(response);
    }
}
