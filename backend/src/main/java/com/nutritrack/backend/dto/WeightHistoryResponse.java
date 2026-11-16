package com.nutritrack.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class WeightHistoryResponse {
    private Double targetWeightKg;
    private List<WeightResponse> entries;
}
