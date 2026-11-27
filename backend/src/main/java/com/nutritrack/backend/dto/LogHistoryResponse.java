package com.nutritrack.backend.dto;

import com.nutritrack.backend.model.Nutrients;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

/** One row per day that has at least one log, for the Analysis page (7 / 30 / 90 days). */
@Getter
@AllArgsConstructor
public class LogHistoryResponse {

    private LocalDate from;
    private LocalDate to;
    private List<Day> days;

    @Getter
    @AllArgsConstructor
    public static class Day {
        private LocalDate date;
        private Nutrients totals;
        private List<Item> items;
    }

    @Getter
    @AllArgsConstructor
    public static class Item {
        private String barcode;
        private String name;
        private double quantityG;
        private double calories;
    }
}
