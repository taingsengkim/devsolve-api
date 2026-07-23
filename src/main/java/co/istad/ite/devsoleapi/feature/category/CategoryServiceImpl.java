package co.istad.ite.devsoleapi.feature.category;

import co.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import co.istad.ite.devsoleapi.feature.category.dto.CategoryPatchRequest;
import co.istad.ite.devsoleapi.feature.category.dto.CategoryRequest;
import co.istad.ite.devsoleapi.feature.category.dto.CategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        String baseSlug = slugify(request.name());
        String uniqueSlug = generateUniqueSlug(baseSlug);

        Category category = Category.builder()
                .name(request.name())
                .slug(uniqueSlug)
                .description(request.description())
                .iconUrl(request.iconUrl())
                .sortOrder(request.sortOrder())
                .isActive(request.isActive() != null ? request.isActive() : true)
                .build();

        Category saved = categoryRepository.save(category);
        return mapToResponse(saved);
    }

    @Override
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<CategoryResponse> getActiveCategoriesSorted() {
        return categoryRepository.findByIsActiveTrueOrderBySortOrderAsc()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public CategoryResponse getCategoryById(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("hello"));
        return mapToResponse(category);
    }

    @Override
    public CategoryResponse getCategoryBySlug(String slug) {
        Category category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with slug: " + slug));
        return mapToResponse(category);
    }

//    @Override
//    @Transactional
//    public CategoryResponse updateCategory(Long id, CategoryPatchRequest request) {
//        Category category = categoryRepository.findById(id)
//                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));
//
//        if (request.name() != null) {
//            category.setName(request.name());
//            // Optionally regenerate slug on name change (uncomment if desired)
////             String newSlug = generateUniqueSlug(slugify(request.name()));
////             category.setSlug(newSlug);
//        }
//
//
//
//        if (request.description() != null) {
//            category.setDescription(request.description());
//        }
//        if (request.iconUrl() != null) {
//            category.setIconUrl(request.iconUrl());
//        }
//        if (request.sortOrder() != null) {
//            category.setSortOrder(request.sortOrder());
//        }
//        if (request.isActive() != null) {
//            category.setIsActive(request.isActive());
//        }
//
//        Category updated = categoryRepository.save(category);
//        return mapToResponse(updated);
//    }

    @Override
    @Transactional
    public void deleteCategory(UUID id) {
        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Category not found with id: " + id);
        }
        categoryRepository.deleteById(id);
    }

    // --- Private Helper Methods ---

    private CategoryResponse mapToResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                category.getIconUrl(),
                category.getSortOrder(),
                category.getIsActive(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }

    private String slugify(String input) {
        if (input == null) return null;
        return input.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .trim();
    }

    private String generateUniqueSlug(String baseSlug) {
        String slug = baseSlug;
        int counter = 1;
        while (categoryRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + counter++;
        }
        return slug;
    }

    @Override
    @Transactional
    public CategoryResponse partialUpdateCategory(UUID id, CategoryPatchRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));

        // Only update fields that are actually sent (not null)
        if (request.name() != null) {
            category.setName(request.name());
            // Optional: Auto-update slug when name changes via PATCH
            // String newSlug = generateUniqueSlug(slugify(request.name()));
            // category.setSlug(newSlug);
        }


//        if (request.slug() != null) {
//            // Check if the new slug is already taken by ANOTHER category (excluding itself)
//            if (categoryRepository.existsBySlugAndIdNot(request.slug(), id)) {
//                throw new RuntimeException("Slug '" + request.slug() + "' is already taken by another category!");
//            }
//
//            String baseSlug = slugify(request.slug());
//            String uniqueSlug = generateUniqueSlug(baseSlug);
//
//            category.setSlug(uniqueSlug);
//        }
        if (request.slug() != null) {
            // 1. Clean the slug (lowercase, spaces → hyphens, remove special chars)
            String cleanSlug = slugify(request.slug());

            // 2. Check if this CLEAN slug is already used by another category
            if (categoryRepository.existsBySlugAndIdNot(cleanSlug, id)) {
                throw new RuntimeException("Slug '" + cleanSlug + "' is already taken!");
            }

            // 3. Set the cleaned slug
            category.setSlug(cleanSlug);
        }


        if (request.description() != null) {
            category.setDescription(request.description());
        }
        if (request.iconUrl() != null) {
            category.setIconUrl(request.iconUrl());
        }
        if (request.sortOrder() != null) {
            category.setSortOrder(request.sortOrder());
        }
        if (request.isActive() != null) {
            category.setIsActive(request.isActive());
        }

        Category updated = categoryRepository.save(category);
        return mapToResponse(updated);
    }
}