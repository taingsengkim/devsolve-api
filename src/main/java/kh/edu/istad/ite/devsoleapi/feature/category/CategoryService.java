package kh.edu.istad.ite.devsoleapi.feature.category;


import kh.edu.istad.ite.devsoleapi.feature.category.dto.CategoryPatchRequest;
import kh.edu.istad.ite.devsoleapi.feature.category.dto.CategoryRequest;
import kh.edu.istad.ite.devsoleapi.feature.category.dto.CategoryResponse;

import java.util.List;
import java.util.UUID;

public interface CategoryService {

    CategoryResponse createCategory(CategoryRequest request);

    List<CategoryResponse> getAllCategories(CategoryScope scope);

    List<CategoryResponse> getActiveCategoriesSorted(CategoryScope scope);

    CategoryResponse getCategoryById(UUID id);

    CategoryResponse getCategoryBySlug(String slug, CategoryScope scope);

//    CategoryResponse updateCategory(Long id, CategoryPatchRequest request);

    void deleteCategory(UUID id);

    CategoryResponse partialUpdateCategory(UUID id, CategoryPatchRequest request);
}
