package com.nutritrack.backend.dto;

import com.nutritrack.backend.model.ActivityLevel;
import com.nutritrack.backend.model.Goal;
import com.nutritrack.backend.model.Sex;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileCalculationResponse {

    private UUID userId;
    private UUID profileId;
    private UUID macroTargetId;

    private String name;
    private Sex sex;
    private double weightKg;
    private double heightCm;
    private int age;
    private ActivityLevel activityLevel;
    private Goal goal;

    private double bmr;
    private double tdee;
    private int calories;
    private double proteinG;
    private double carbsG;
    private double fatG;
}
