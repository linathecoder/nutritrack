package com.nutritrack.backend.repository;

import com.nutritrack.backend.model.FoodProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodProductRepository extends JpaRepository<FoodProduct, String> {
}