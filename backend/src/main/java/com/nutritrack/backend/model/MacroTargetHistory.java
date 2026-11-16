package com.nutritrack.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "date"}))
@Getter @Setter
public class MacroTargetHistory {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JsonIgnore
    private User user;

    private LocalDate date;

    private int calories;
    private double proteinG, carbsG, fatG;
}
