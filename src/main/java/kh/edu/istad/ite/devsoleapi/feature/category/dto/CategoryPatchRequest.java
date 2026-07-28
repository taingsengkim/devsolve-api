package kh.edu.istad.ite.devsoleapi.feature.category.dto;



import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.devsoleapi.feature.category.CategoryScope;

public record CategoryPatchRequest(
        @Size(max = 50, message = "Name must be less than 50 characters")
        String name,

        @Size(max = 50, message = "Slug must be less than 50 characters")
        String slug ,

        CategoryScope scope,

        @Size(max = 500, message = "Description must be less than 500 characters")
        String description,

        @Size(max = 255, message = "Icon URL must be less than 255 characters")
        String iconUrl,

        Integer sortOrder,

        Boolean isActive
) {}
