package com.nutritrack.backend.repository;

import com.nutritrack.backend.model.LogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LogEntryRepository extends JpaRepository<LogEntry, UUID> {
    Optional<LogEntry> findByIdAndDailyLogUserId(UUID id, UUID userId);

    /** Entries of meals created in the app (barcode "meal-..."), newest first. My Meals page. */
    @Query("select e from LogEntry e "
            + "where e.dailyLog.user.id = :userId and e.food.barcode like 'meal-%' "
            + "order by e.loggedAt desc")
    List<LogEntry> findMealEntriesByUser(@Param("userId") UUID userId);

    /** The 500 most recent entries of a user (meals, scanned products, foods). Log Meal search. */
    List<LogEntry> findTop500ByDailyLogUserIdOrderByLoggedAtDesc(UUID userId);
}
