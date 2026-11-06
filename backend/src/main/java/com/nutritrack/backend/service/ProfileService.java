package com.nutritrack.backend.service;

import com.nutritrack.backend.dto.ProfileCalculationRequest;
import com.nutritrack.backend.dto.ProfileCalculationResponse;
import com.nutritrack.backend.model.MacroTarget;
import com.nutritrack.backend.model.User;
import com.nutritrack.backend.model.UserProfile;
import com.nutritrack.backend.repository.MacroTargetRepository;
import com.nutritrack.backend.repository.UserProfileRepository;
import com.nutritrack.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final MacroTargetRepository macroTargetRepository;
    private final CalorieCalculatorService calorieCalculatorService;

    public ProfileCalculationResponse previewCalculation(ProfileCalculationRequest request) {
        int age = (request.getAge() != null && request.getAge() > 0) ? request.getAge() : 25;
        CalorieCalculatorService.CalculationResult calc = calorieCalculatorService.calculate(
                request.getSex(),
                request.getWeightKg(),
                request.getHeightCm(),
                age,
                request.getActivityLevel(),
                request.getGoal()
        );

        return ProfileCalculationResponse.builder()
                .userId(request.getUserId())
                .name(request.getName())
                .sex(request.getSex())
                .weightKg(request.getWeightKg())
                .heightCm(request.getHeightCm())
                .age(age)
                .activityLevel(request.getActivityLevel())
                .goal(request.getGoal())
                .bmr(calc.bmr())
                .tdee(calc.tdee())
                .calories(calc.calories())
                .proteinG(calc.proteinG())
                .carbsG(calc.carbsG())
                .fatG(calc.fatG())
                .build();
    }

    @Transactional
    public ProfileCalculationResponse calculateAndSaveProfile(ProfileCalculationRequest request) {
        if (request.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required to save profile to database");
        }

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: " + request.getUserId()));

        int age = (request.getAge() != null && request.getAge() > 0) ? request.getAge() : 25;

        CalorieCalculatorService.CalculationResult calc = calorieCalculatorService.calculate(
                request.getSex(),
                request.getWeightKg(),
                request.getHeightCm(),
                age,
                request.getActivityLevel(),
                request.getGoal()
        );

        UserProfile profile = userProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    UserProfile p = new UserProfile();
                    p.setUser(user);
                    return p;
                });

        profile.setName(request.getName());
        profile.setSex(request.getSex());
        profile.setWeightKg(request.getWeightKg());
        profile.setHeightCm(request.getHeightCm());
        profile.setAge(age);
        profile.setActivityLevel(request.getActivityLevel());
        profile.setGoal(request.getGoal());
        UserProfile savedProfile = userProfileRepository.save(profile);

        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName());
            userRepository.save(user);
        }

        MacroTarget macroTarget = macroTargetRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    MacroTarget m = new MacroTarget();
                    m.setUser(user);
                    return m;
                });

        macroTarget.setCalories(calc.calories());
        macroTarget.setProteinG(calc.proteinG());
        macroTarget.setCarbsG(calc.carbsG());
        macroTarget.setFatG(calc.fatG());
        MacroTarget savedMacroTarget = macroTargetRepository.save(macroTarget);

        return ProfileCalculationResponse.builder()
                .userId(user.getId())
                .profileId(savedProfile.getId())
                .macroTargetId(savedMacroTarget.getId())
                .name(savedProfile.getName())
                .sex(savedProfile.getSex())
                .weightKg(savedProfile.getWeightKg())
                .heightCm(savedProfile.getHeightCm())
                .age(savedProfile.getAge())
                .activityLevel(savedProfile.getActivityLevel())
                .goal(savedProfile.getGoal())
                .bmr(calc.bmr())
                .tdee(calc.tdee())
                .calories(savedMacroTarget.getCalories())
                .proteinG(savedMacroTarget.getProteinG())
                .carbsG(savedMacroTarget.getCarbsG())
                .fatG(savedMacroTarget.getFatG())
                .build();
    }

    @Transactional(readOnly = true)
    public ProfileCalculationResponse getProfileByUserId(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: " + userId));

        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found for user: " + userId));

        MacroTarget macroTarget = macroTargetRepository.findByUserId(userId)
                .orElse(null);

        CalorieCalculatorService.CalculationResult calc = calorieCalculatorService.calculate(
                profile.getSex(),
                profile.getWeightKg(),
                profile.getHeightCm(),
                profile.getAge(),
                profile.getActivityLevel(),
                profile.getGoal()
        );

        return ProfileCalculationResponse.builder()
                .userId(user.getId())
                .profileId(profile.getId())
                .macroTargetId(macroTarget != null ? macroTarget.getId() : null)
                .name(profile.getName())
                .sex(profile.getSex())
                .weightKg(profile.getWeightKg())
                .heightCm(profile.getHeightCm())
                .age(profile.getAge())
                .activityLevel(profile.getActivityLevel())
                .goal(profile.getGoal())
                .bmr(calc.bmr())
                .tdee(calc.tdee())
                .calories(macroTarget != null ? macroTarget.getCalories() : calc.calories())
                .proteinG(macroTarget != null ? macroTarget.getProteinG() : calc.proteinG())
                .carbsG(macroTarget != null ? macroTarget.getCarbsG() : calc.carbsG())
                .fatG(macroTarget != null ? macroTarget.getFatG() : calc.fatG())
                .build();
    }
}
