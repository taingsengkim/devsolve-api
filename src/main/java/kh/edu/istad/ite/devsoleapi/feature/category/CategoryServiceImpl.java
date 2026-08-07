package kh.edu.istad.ite.devsoleapi.feature.category;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.common.storage.ImageStorageService;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.category.dto.CategoryPatchRequest;
import kh.edu.istad.ite.devsoleapi.feature.category.dto.CategoryRequest;
import kh.edu.istad.ite.devsoleapi.feature.category.dto.CategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ImageStorageService imageStorageService;

    @Override
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {

        if (!AuthUtils.hasRole("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ADMIN can create categories");
        }

        String baseSlug = slugify(request.name());
        String uniqueSlug = generateUniqueSlug(baseSlug, request.scope());

        Category category = new Category();
        category.setName(request.name());
        category.setSlug(uniqueSlug);
        category.setScope(request.scope());
        category.setDescription(request.description());
        category.setIconUrl(request.iconUrl());
        category.setSortOrder(request.sortOrder());

        if (request.isActive() != null) {
            category.setIsActive(request.isActive());
        }
        Category saved = categoryRepository.save(category);
        return mapToResponse(saved);
    }

    @Override
    public List<CategoryResponse> getAllCategories(CategoryScope scope) {
        List<Category> categories = scope == null
                ? categoryRepository.findAll()
                : categoryRepository.findAllByScope(scope);
        return categories
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<CategoryResponse> getActiveCategoriesSorted(
            CategoryScope scope
    ) {
        List<Category> categories = scope == null
                ? categoryRepository.findByIsActiveTrueOrderBySortOrderAsc()
                : categoryRepository
                .findByScopeAndIsActiveTrueOrderBySortOrderAsc(scope);
        return categories
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public CategoryResponse getCategoryById(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with this uuid"));
        return mapToResponse(category);
    }

    @Override
    public CategoryResponse getCategoryBySlug(
            String slug,
            CategoryScope scope
    ) {
        if (scope == null) {
            List<Category> matches = categoryRepository.findAllBySlug(slug);
            if (matches.size() > 1) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Category scope is required because this slug exists "
                                + "in multiple scopes"
                );
            }
            Category category = matches.stream()
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Category not found with this slug"
                    ));
            return mapToResponse(category);
        }
        Category category = categoryRepository
                .findByScopeAndSlug(scope, slug)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found with this slug and scope"
                ));
        return mapToResponse(category);
    }


    @Override
    @Transactional
    public void deleteCategory(UUID id) {

        if (!AuthUtils.hasRole("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ADMIN can delete categories");
        }

        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Category not found with this uuid");
        }
        categoryRepository.deleteById(id);
    }


    private CategoryResponse mapToResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getScope(),
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

    private String generateUniqueSlug(
            String baseSlug,
            CategoryScope scope
    ) {
        String slug = baseSlug;
        int counter = 1;
        while (categoryRepository.existsByScopeAndSlug(scope, slug)) {
            slug = baseSlug + "-" + counter++;
        }
        return slug;
    }

    @Override
    @Transactional
    public CategoryResponse partialUpdateCategory(UUID id, CategoryPatchRequest request) {
        if (!AuthUtils.hasRole("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ADMIN can update categories");
        }
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with this uuid"));

        if (request.name() != null) {
            category.setName(request.name());
        }


        String effectiveSlug = request.slug() == null
                ? category.getSlug()
                : slugify(request.slug());
        CategoryScope effectiveScope = request.scope() == null
                ? category.getScope()
                : request.scope();
        if (categoryRepository.existsByScopeAndSlugAndIdNot(
                effectiveScope,
                effectiveSlug,
                id
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Slug '" + effectiveSlug + "' is already used in this scope"
            );
        }

        if (request.slug() != null) {
            category.setSlug(effectiveSlug);
        }
        if (request.scope() != null) {
            category.setScope(request.scope());
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

    @Override
    @Transactional
    public CategoryResponse uploadIcon(UUID id, MultipartFile file) {
        if (!AuthUtils.hasRole("ADMIN")) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only ADMIN can change category icons"
            );
        }
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found with this uuid"
                ));

        String iconUrl = imageStorageService.replace(
                "categories/" + category.getId(),
                category.getIconUrl(),
                file
        );
        category.setIconUrl(iconUrl);
        return mapToResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public CategoryResponse removeIcon(UUID id) {
        if (!AuthUtils.hasRole("ADMIN")) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only ADMIN can change category icons"
            );
        }
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found with this uuid"
                ));

        imageStorageService.remove(category.getIconUrl());
        category.setIconUrl(null);
        return mapToResponse(categoryRepository.save(category));
    }
}
