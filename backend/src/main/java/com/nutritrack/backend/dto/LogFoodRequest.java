package com.nutritrack.backend.dto;

import com.nutritrack.backend.model.Nutrients;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LogFoodRequest {

    @NotBlank(message = "Barcode is required")
    @Size(max = 64, message = "Barcode is too long")
    private String barcode;

    private String name;

    private String ingredients;


    @Size(max = 1000, message = "Image URL is too long")
    private String imageUrl;


    @NotNull(message = "Nutrients per 100g are required")
    private Nutrients per100g;

  
    @DecimalMin(value = "0.1", message = "Quantity must be greater than 0")
    @DecimalMax(value = "10000", message = "Quantity is too large")
    private double quantityG;
}
