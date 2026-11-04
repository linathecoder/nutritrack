package com.nutritrack.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter @Setter
public class FoodProduct {
    @Id
    private String barcode;

    private String name;

    @Column(length = 4000)
    private String ingredients;

    @Embedded
    private Nutrients per100g;

    @Enumerated(EnumType.STRING)
    private Source source;
}