package com.nutritrack.backend.service;

import com.nutritrack.backend.dto.WeightHistoryResponse;
import com.nutritrack.backend.dto.WeightRequest;
import com.nutritrack.backend.dto.WeightResponse;
import com.nutritrack.backend.model.User;
import com.nutritrack.backend.model.UserProfile;
import com.nutritrack.backend.model.WeightEntry;
import com.nutritrack.backend.repository.UserProfileRepository;
import com.nutritrack.backend.repository.UserRepository;
import com.nutritrack.backend.repository.WeightEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WeightService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Tunis");
    private static final int MAX_DAYS = 730;

    private final WeightEntryRepository weightEntryRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;

    @Transactional
    public WeightResponse log(UUID userId, WeightRequest req) {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate date = req.getDate() != null ? req.getDate() : today;
        if (date.isAfter(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The date cannot be in the future");
        }
        if (date.isBefore(today.minusYears(2))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The date is too far in the past");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        WeightEntry entry = weightEntryRepository.findByUserIdAndDate(userId, date).orElseGet(() -> {
            WeightEntry e = new WeightEntry();
            e.setUser(user);
            e.setDate(date);
            return e;
        });
        entry.setWeightKg(req.getWeightKg());
        entry = weightEntryRepository.saveAndFlush(entry);

        WeightEntry latest = weightEntryRepository.findFirstByUserIdOrderByDateDesc(userId).orElse(entry);
        if (latest.getDate().equals(entry.getDate())) {
            userProfileRepository.findByUserId(userId).ifPresent(profile -> {
                profile.setWeightKg(req.getWeightKg());
                userProfileRepository.save(profile);
            });
        }
        return new WeightResponse(entry.getDate(), entry.getWeightKg());
    }

    @Transactional(readOnly = true)
    public WeightHistoryResponse history(UUID userId, int days) {
        int range = Math.max(1, Math.min(days, MAX_DAYS));
        LocalDate from = LocalDate.now(ZONE).minusDays(range - 1L);
        List<WeightResponse> entries = weightEntryRepository
                .findByUserIdAndDateGreaterThanEqualOrderByDateAsc(userId, from).stream()
                .map(e -> new WeightResponse(e.getDate(), e.getWeightKg()))
                .toList();
        Double target = userProfileRepository.findByUserId(userId).map(UserProfile::getTargetWeightKg).orElse(null);
        return new WeightHistoryResponse(target, entries);
    }
}
