package com.nutritrack.backend.service;

import com.nutritrack.backend.dto.DailyLogResponse;
import com.nutritrack.backend.dto.LogFoodRequest;
import com.nutritrack.backend.dto.LogHistoryResponse;
import com.nutritrack.backend.dto.SavedMealResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
                        e.getFood().getImageUrl(),
                        e.getFood().getIngredients()))
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
            f.setSource(barcode.startsWith("meal-") ? Source.MANUAL : Source.OPEN_FOOD_FACTS);
            return foodProductRepository.save(f);
        });
        if (food.getPer100g() == null) {
            food.setPer100g(per100g);
        }
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
                food.getImageUrl(),
                food.getIngredients());
    }

    @Transactional
    public DailyLogResponse.Entry updateEntry(UUID userId, UUID entryId, LogFoodRequest req) {
        LogEntry entry = logEntryRepository.findByIdAndDailyLogUserId(entryId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Log entry not found"));

        FoodProduct food = entry.getFood();
        if (food.getSource() != Source.MANUAL || food.getBarcode() == null
                || !food.getBarcode().startsWith("meal-")) {
            throw new IllegalArgumentException("Only custom meals can be edited");
        }
        if (!food.getBarcode().equals(req.getBarcode().trim())) {
            throw new IllegalArgumentException("Meal identifier cannot be changed");
        }

        Nutrients per100g = req.getPer100g();
        if (!isValid(per100g.getCalories()) || !isValid(per100g.getProteinG())
                || !isValid(per100g.getCarbsG()) || !isValid(per100g.getFatG())) {
            throw new IllegalArgumentException("Nutrient values must be zero or positive numbers");
        }
        if (!Double.isFinite(req.getQuantityG()) || req.getQuantityG() < 0.1
                || req.getQuantityG() > 10000) {
            throw new IllegalArgumentException("Quantity must be between 0.1 and 10000 grams");
        }

        food.setName(truncate(blankToDefault(req.getName(), "Custom meal"), 255));
        food.setIngredients(truncate(req.getIngredients(), 4000));
        food.setPer100g(per100g);
        String imageUrl = cleanImageUrl(req.getImageUrl());
        if (imageUrl != null) food.setImageUrl(imageUrl);

        entry.setQuantityG(req.getQuantityG());
        entry.recompute();
        return toResponse(entry);
    }

    @Transactional
    public void deleteEntry(UUID userId, UUID entryId) {
        LogEntry entry = logEntryRepository.findByIdAndDailyLogUserId(entryId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Log entry not found"));
        logEntryRepository.delete(entry);
    }

    // ── Analysis page: one row per logged day in the last N days ─────────────

    @Transactional(readOnly = true)
    public LogHistoryResponse history(UUID userId, int days) {
        int n = Math.min(Math.max(days, 1), 400);
        LocalDate to = LocalDate.now(ZONE);
        LocalDate from = to.minusDays(n - 1L);

        List<LogHistoryResponse.Day> out = new ArrayList<>();
        for (DailyLog log : dailyLogRepository.findByUserIdAndDateBetweenOrderByDateAsc(userId, from, to)) {
            Nutrients totals = new Nutrients();
            List<LogHistoryResponse.Item> items = new ArrayList<>();
            for (LogEntry e : log.getEntries()) {
                Nutrients c = e.getComputed() != null ? e.getComputed() : new Nutrients();
                totals = totals.plus(c);
                items.add(new LogHistoryResponse.Item(
                        e.getFood().getBarcode(), e.getFood().getName(), e.getQuantityG(), c.getCalories()));
            }
            out.add(new LogHistoryResponse.Day(log.getDate(), totals, items));
        }
        return new LogHistoryResponse(from, to, out);
    }

    // ── My Meals page: meals created by the user (barcode "meal-..."), newest first ──

    @Transactional(readOnly = true)
    public List<SavedMealResponse> savedMeals(UUID userId) {
        Map<String, SavedMealResponse> byBarcode = new LinkedHashMap<>();
        for (LogEntry e : logEntryRepository.findMealEntriesByUser(userId)) {
            FoodProduct food = e.getFood();
            if (food == null || food.getBarcode() == null || byBarcode.containsKey(food.getBarcode())) continue;
            byBarcode.put(food.getBarcode(), toSavedMeal(e, food));
        }
        return new ArrayList<>(byBarcode.values());
    }

    // ── Log Meal search: everything the user already logged (meals, scanned products, foods) ──

    @Transactional(readOnly = true)
    public List<SavedMealResponse> loggedFoods(UUID userId, String q) {
        String needle = q == null ? "" : q.trim().toLowerCase();
        Map<String, SavedMealResponse> byBarcode = new LinkedHashMap<>();
        for (LogEntry e : logEntryRepository.findTop500ByDailyLogUserIdOrderByLoggedAtDesc(userId)) {
            FoodProduct food = e.getFood();
            if (food == null || food.getBarcode() == null || byBarcode.containsKey(food.getBarcode())) continue;
            if (!needle.isEmpty()) {
                String name = food.getName() == null ? "" : food.getName().toLowerCase();
                String ingredients = food.getIngredients() == null ? "" : food.getIngredients().toLowerCase();
                if (!name.contains(needle) && !ingredients.contains(needle)) continue;
            }
            byBarcode.put(food.getBarcode(), toSavedMeal(e, food));
            if (byBarcode.size() >= 30) break;
        }
        return new ArrayList<>(byBarcode.values());
    }

    private static SavedMealResponse toSavedMeal(LogEntry e, FoodProduct food) {
        Nutrients per100g = food.getPer100g() != null ? food.getPer100g() : new Nutrients();
        double qty = e.getQuantityG();
        return new SavedMealResponse(
                e.getId(), food.getBarcode(), food.getName(), food.getIngredients(), food.getImageUrl(),
                per100g, qty, per100g.scale(qty / 100.0), e.getLoggedAt());
    }

    private static DailyLogResponse.Entry toResponse(LogEntry entry) {
        FoodProduct food = entry.getFood();
        return new DailyLogResponse.Entry(entry.getId(), food.getBarcode(), food.getName(),
                entry.getQuantityG(), entry.getComputed(), entry.getLoggedAt(),
                food.getImageUrl(), food.getIngredients());
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
