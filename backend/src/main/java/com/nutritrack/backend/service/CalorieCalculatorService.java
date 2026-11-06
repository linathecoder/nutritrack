package com.nutritrack.backend.service;

import com.nutritrack.backend.model.ActivityLevel;
import com.nutritrack.backend.model.Goal;
import com.nutritrack.backend.model.Sex;
import org.springframework.stereotype.Service;

@Service
public class CalorieCalculatorService {

    public record CalculationResult(
            double bmr,
            double tdee,
            int calories,
            double proteinG,
            double carbsG,
            double fatG
    ) {}

    public CalculationResult calculate(
            Sex sex,
            double weightKg,
            double heightCm,
            int age,
            ActivityLevel activityLevel,
            Goal goal
    ) {
        int effectiveAge = age > 0 ? age : 25;

        double bmr = (10.0 * weightKg) + (6.25 * heightCm) - (5.0 * effectiveAge);
        if (sex == Sex.MALE) {
            bmr += 5.0;
        } else {
            bmr -= 161.0;
        }

        double multiplier = switch (activityLevel) {
            case SEDENTARY -> 1.2;
            case LIGHT -> 1.375;
            case MODERATE -> 1.55;
            case ACTIVE -> 1.725;
        };
        double tdee = bmr * multiplier;

        double targetCalories = switch (goal) {
            case WEIGHT_LOSS -> tdee - 500.0;
            case MUSCLE_GAIN -> tdee + 350.0;
            case MAINTENANCE -> tdee;
        };

        int finalCalories = (int) Math.round(Math.max(1200.0, targetCalories));

        double proteinMultiplier = (goal == Goal.MAINTENANCE) ? 1.8 : 2.0;
        double proteinG = roundToOneDecimal(weightKg * proteinMultiplier);

        double fatG = roundToOneDecimal((finalCalories * 0.25) / 9.0);

        double consumedCalories = (proteinG * 4.0) + (fatG * 9.0);
        double carbsG = roundToOneDecimal(Math.max(0.0, (finalCalories - consumedCalories) / 4.0));

        return new CalculationResult(
                roundToOneDecimal(bmr),
                roundToOneDecimal(tdee),
                finalCalories,
                proteinG,
                carbsG,
                fatG
        );
    }

    private double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}

