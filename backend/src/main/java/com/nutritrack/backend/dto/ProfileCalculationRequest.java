package com.nutritrack.backend.dto;

import com.nutritrack.backend.model.ActivityLevel;
import com.nutritrack.backend.model.Goal;
import com.nutritrack.backend.model.Sex;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ProfileCalculationRequest {

    private UUID userId;

    private String name;

    @NotNull(message = "Sex is required (MALE, FEMALE)")
    private Sex sex;

    @Positive(message = "Weight must be greater than 0")
    private double weightKg;

    @Positive(message = "Height must be greater than 0")
    private double heightCm;

    @Positive(message = "Age must be greater than 0")
    private Integer age = 25;

    @NotNull(message = "Activity level is required (SEDENTARY, LIGHT, MODERATE, ACTIVE)")
    private ActivityLevel activityLevel;

    @NotNull(message = "Goal is required (WEIGHT_LOSS, MAINTENANCE, MUSCLE_GAIN)")
    private Goal goal;
}
