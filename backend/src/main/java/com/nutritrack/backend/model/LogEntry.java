package com.nutritrack.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter @Setter
public class LogEntry {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JsonIgnore
    private DailyLog dailyLog;

    @ManyToOne(optional = false)
    private FoodProduct food;

    private double quantityG;

    @Embedded
    private Nutrients computed;


    public void recompute() {
        computed = food.getPer100g().scale(quantityG / 100.0);
    }


    public void updateQuantity(double g) {
        if (g <= 0) throw new IllegalArgumentException("Quantity must be > 0");
        this.quantityG = g;
        recompute();
    }
}