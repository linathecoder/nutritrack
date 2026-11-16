package com.nutritrack.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class WeightResponse {
    private LocalDate date;
    private double weightKg;
}
