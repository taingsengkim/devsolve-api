package kh.edu.istad.ite.devsoleapi.feature.category.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import kh.edu.istad.ite.devsoleapi.feature.category.CategoryScope;

public record CategoryRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 50, message = "Name must be less than 50 characters")
        String name,

        @NotNull(message = "Category scope is required")
        CategoryScope scope,

        @Size(max = 500, message = "Description must be less than 500 characters")
        String description,

        @Size(max = 255, message = "Icon URL must be less than 255 characters")
        String iconUrl,

        Integer sortOrder,

        Boolean isActive
) {}

