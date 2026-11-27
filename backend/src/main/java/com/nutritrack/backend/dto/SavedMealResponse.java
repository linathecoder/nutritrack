package com.nutritrack.backend.dto;

import com.nutritrack.backend.model.Nutrients;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/** A meal the user created with "Create a meal", as shown on the My Meals page. */
@Getter
@AllArgsConstructor
public class SavedMealResponse {
    /** Most recent log entry of this meal (needed by the app to edit the meal). */
    private UUID lastEntryId;
    private String barcode;
    private String name;
    /** JSON written by the app (ingredient list). */
    private String ingredients;
    private String imageUrl;
    private Nutrients per100g;
    /** Total weight of the meal, as it was last logged. */
    private double quantityG;
    /** Calories and macros of the whole meal (per100g scaled to quantityG). */
    private Nutrients totals;
    private Instant lastLoggedAt;
}
