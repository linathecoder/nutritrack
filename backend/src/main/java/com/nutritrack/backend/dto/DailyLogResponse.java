package com.nutritrack.backend.dto;

import com.nutritrack.backend.model.Nutrients;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class DailyLogResponse {

    private LocalDate date;
    private Nutrients totals;
    private List<Entry> entries;

    @Getter
    @AllArgsConstructor
    public static class Entry {
        private UUID id;
        private String barcode;
        private String name;
        private double quantityG;
        private Nutrients computed;
        private Instant loggedAt;
    }
}
