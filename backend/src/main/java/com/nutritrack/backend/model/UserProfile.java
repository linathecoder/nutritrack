package com.nutritrack.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter @Setter
public class UserProfile {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(optional = false)
    @JoinColumn(unique = true)
    @JsonIgnore
    private User user;

    private String name;
    private double weightKg, heightCm;
    private int age;

    @Enumerated(EnumType.STRING) private Sex sex;
    @Enumerated(EnumType.STRING) private ActivityLevel activityLevel;
    @Enumerated(EnumType.STRING) private WorkoutFrequency workoutFrequency;
    @Enumerated(EnumType.STRING) private Goal goal;
}