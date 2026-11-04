package com.nutritrack.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter @Setter
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "date"}))
public class DailyLog {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    private LocalDate date;

    @OneToMany(mappedBy = "dailyLog", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LogEntry> entries = new ArrayList<>();

    public Nutrients totals() {
        return entries.stream()
                .map(LogEntry::getComputed)
                .reduce(new Nutrients(), Nutrients::plus);
    }

    public boolean isToday() {
        return date.equals(LocalDate.now(ZoneId.of("Africa/Tunis")));
    }
}