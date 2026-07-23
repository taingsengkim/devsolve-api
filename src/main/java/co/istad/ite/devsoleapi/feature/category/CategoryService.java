package co.istad.ite.devsoleapi.feature.category;


import co.istad.ite.devsoleapi.feature.category.dto.CategoryPatchRequest;
import co.istad.ite.devsoleapi.feature.category.dto.CategoryRequest;
import co.istad.ite.devsoleapi.feature.category.dto.CategoryResponse;

import java.util.List;

public interface CategoryService {

    CategoryResponse createCategory(CategoryRequest request);

    List<CategoryResponse> getAllCategories();

    List<CategoryResponse> getActiveCategoriesSorted();

    CategoryResponse getCategoryById(Long id);

    CategoryResponse getCategoryBySlug(String slug);

//    CategoryResponse updateCategory(Long id, CategoryPatchRequest request);

    void deleteCategory(Long id);

    CategoryResponse partialUpdateCategory(Long id, CategoryPatchRequest request);
}