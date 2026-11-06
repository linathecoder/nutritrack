package com.nutritrack.backend.service;

import com.nutritrack.backend.dto.DailyLogResponse;
import com.nutritrack.backend.model.LogEntry;
import com.nutritrack.backend.model.Nutrients;
import com.nutritrack.backend.repository.DailyLogRepository;
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
                        e.getLoggedAt()))
                .toList();

        return new DailyLogResponse(today, totals, entries);
    }
}
