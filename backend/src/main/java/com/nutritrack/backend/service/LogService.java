package com.nutritrack.backend.service;

import com.nutritrack.backend.dto.DailyLogResponse;
import com.nutritrack.backend.dto.LogFoodRequest;
import com.nutritrack.backend.model.DailyLog;
import com.nutritrack.backend.model.FoodProduct;
import com.nutritrack.backend.model.LogEntry;
import com.nutritrack.backend.model.Nutrients;
import com.nutritrack.backend.model.Source;
import com.nutritrack.backend.repository.DailyLogRepository;
import com.nutritrack.backend.repository.FoodProductRepository;
import com.nutritrack.backend.repository.LogEntryRepository;
import com.nutritrack.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LogService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Tunis");

    private final DailyLogRepository dailyLogRepository;
    private final FoodProductRepository foodProductRepository;
    private final LogEntryRepository logEntryRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public DailyLogResponse getToday(UUID userId) {
        LocalDate today = LocalDate.now(ZONE);

        List<LogEntry> logEntries = dailyLogRepository.findByUserIdAndDate(userId, today)
                .map(log -> log.getEntries().stream()
                        .sorted(Comparator.comparing(LogEntry::getLoggedAt,
                                Comparator.nullsFirst(Comparator.<Instant>naturalOrder())))
                        .toList())
                .orElse(List.of());

        Nutrients totals = new Nutrients();
        for (LogEntry e : logEntries) {
            if (e.getComputed() != null) {
                totals = totals.plus(e.getComputed());
            }
        }

        List<DailyLogResponse.Entry> entries = logEntries.stream()
                .map(e -> new DailyLogResponse.Entry(
                        e.getId(),
                        e.getFood().getBarcode(),
                        e.getFood().getName(),
                        e.getQuantityG(),
                        e.getComputed() != null ? e.getComputed() : new Nutrients(),
                        e.getLoggedAt(),
                        e.getFood().getImageUrl()))
                .toList();

        return new DailyLogResponse(today, totals, entries);
    }

    @Transactional
    public DailyLogResponse.Entry addEntry(UUID userId, LogFoodRequest req) {
        Nutrients per100g = req.getPer100g();
        if (!isValid(per100g.getCalories()) || !isValid(per100g.getProteinG())
                || !isValid(per100g.getCarbsG()) || !isValid(per100g.getFatG())) {
            throw new IllegalArgumentException("Nutrient values must be zero or positive numbers");
        }

        String barcode = req.getBarcode().trim();
        String imageUrl = cleanImageUrl(req.getImageUrl());

        FoodProduct food = foodProductRepository.findById(barcode).orElseGet(() -> {
            FoodProduct f = new FoodProduct();
            f.setBarcode(barcode);
            f.setName(truncate(blankToDefault(req.getName(), "Unknown product"), 255));
            f.setIngredients(truncate(req.getIngredients(), 4000));
            f.setImageUrl(imageUrl);
            f.setPer100g(per100g);
            f.setSource(Source.OPEN_FOOD_FACTS);
            return foodProductRepository.save(f);
        });
        if (food.getPer100g() == null) {
            food.setPer100g(per100g);
        }
        // Products saved before photos were supported get their photo the next time they are logged.
        if (food.getImageUrl() == null && imageUrl != null) {
            food.setImageUrl(imageUrl);
        }

        LocalDate today = LocalDate.now(ZONE);
        DailyLog dailyLog = dailyLogRepository.findByUserIdAndDate(userId, today).orElseGet(() -> {
            DailyLog log = new DailyLog();
            log.setUser(userRepository.getReferenceById(userId));
            log.setDate(today);
            return dailyLogRepository.save(log);
        });

        LogEntry entry = new LogEntry();
        entry.setDailyLog(dailyLog);
        entry.setFood(food);
        entry.setQuantityG(req.getQuantityG());
        entry.recompute();
        entry = logEntryRepository.save(entry);

        return new DailyLogResponse.Entry(
                entry.getId(),
                food.getBarcode(),
                food.getName(),
                entry.getQuantityG(),
                entry.getComputed(),
                entry.getLoggedAt(),
                food.getImageUrl());
    }


    private static String cleanImageUrl(String url) {
        if (url == null) return null;
        String u = url.trim();
        return (u.startsWith("https://") || u.startsWith("http://")) && u.length() <= 1000 ? u : null;
    }

    private static boolean isValid(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v) && v >= 0;
    }

    private static String blankToDefault(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s.trim();
    }

    private static String truncate(String s, int max) {
        return (s == null || s.length() <= max) ? s : s.substring(0, max);
    }
}
