package kh.edu.istad.ite.devsoleapi.feature.category;


import kh.edu.istad.ite.devsoleapi.feature.category.dto.CategoryPatchRequest;
import kh.edu.istad.ite.devsoleapi.feature.category.dto.CategoryRequest;
import kh.edu.istad.ite.devsoleapi.feature.category.dto.CategoryResponse;

import java.util.List;
import java.util.UUID;

public interface CategoryService {

    CategoryResponse createCategory(CategoryRequest request);

    List<CategoryResponse> getAllCategories();

    List<CategoryResponse> getActiveCategoriesSorted();

    CategoryResponse getCategoryById(UUID id);

    CategoryResponse getCategoryBySlug(String slug);

//    CategoryResponse updateCategory(Long id, CategoryPatchRequest request);

    void deleteCategory(UUID id);

    CategoryResponse partialUpdateCategory(UUID id, CategoryPatchRequest request);
}