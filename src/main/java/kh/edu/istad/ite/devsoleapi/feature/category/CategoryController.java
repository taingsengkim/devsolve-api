package kh.edu.istad.ite.devsoleapi.feature.category;


import kh.edu.istad.ite.devsoleapi.feature.category.dto.CategoryPatchRequest;
import kh.edu.istad.ite.devsoleapi.feature.category.dto.CategoryRequest;
import kh.edu.istad.ite.devsoleapi.feature.category.dto.CategoryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        CategoryResponse response = categoryService.createCategory(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getAllCategories(
            @RequestParam(required = false) CategoryScope scope
    ) {
        return ResponseEntity.ok(categoryService.getAllCategories(scope));
    }

    @GetMapping("/active")
    public ResponseEntity<List<CategoryResponse>> getActiveCategories(
            @RequestParam(required = false) CategoryScope scope
    ) {
        return ResponseEntity.ok(
                categoryService.getActiveCategoriesSorted(scope)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> getCategoryById(@PathVariable UUID id) {
        return ResponseEntity.ok(categoryService.getCategoryById(id));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<CategoryResponse> getCategoryBySlug(
            @PathVariable String slug,
            @RequestParam(required = false) CategoryScope scope
    ) {
        return ResponseEntity.ok(
                categoryService.getCategoryBySlug(slug, scope)
        );
    }

//    @PutMapping("/{id}")
//    public ResponseEntity<CategoryResponse> updateCategory(
//            @PathVariable Long id,
//            @Valid @RequestBody CategoryPatchRequest request) {
//        return ResponseEntity.ok(categoryService.updateCategory(id, request));
//    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable UUID id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CategoryResponse> partialUpdateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody CategoryPatchRequest request) {
        return ResponseEntity.ok(categoryService.partialUpdateCategory(id, request));
    }
}

