package com.nutritrack.backend.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Nutrients {
    private double calories, proteinG, carbsG, fatG;

    public Nutrients scale(double f) {
        return new Nutrients(calories * f, proteinG * f, carbsG * f, fatG * f);
    }

    public Nutrients plus(Nutrients o) {
        return new Nutrients(calories + o.calories, proteinG + o.proteinG,
                carbsG + o.carbsG, fatG + o.fatG);
    }
}