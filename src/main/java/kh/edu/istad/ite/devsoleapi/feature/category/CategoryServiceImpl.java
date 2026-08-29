package kh.edu.istad.ite.devsoleapi.feature.category;

import kh.edu.istad.ite.devsoleapi.common.cache.CacheNames;
import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.common.storage.ImageStorageService;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.category.dto.CategoryPatchRequest;
import kh.edu.istad.ite.devsoleapi.feature.category.dto.CategoryRequest;
import kh.edu.istad.ite.devsoleapi.feature.category.dto.CategoryResponse;
import kh.edu.istad.ite.devsoleapi.feature.problem.ProblemRepository;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCasesRepository;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowcaseRevisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final ProblemRepository problemRepository;
    private final ShowCasesRepository showCasesRepository;
    private final ShowcaseRevisionRepository showcaseRevisionRepository;

    /**
     * Drops the whole categories cache, here and on every other write below: a
     * new or renamed category invalidates both listings and every scope filter
     * of each. Eviction happens only on a successful return, so a create that
     * throws leaves a cache that is still correct.
     */
    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.CATEGORIES, allEntries = true)
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

    /**
     * Cached: read on nearly every page load, written only by an admin, and
     * identical for every viewer — nothing in a {@link CategoryResponse} is
     * per-user, which is what makes a shared cache safe here.
     */
    @Override
    @Cacheable(
            cacheNames = CacheNames.CATEGORIES,
            key = "'all:' + (#scope == null ? 'any' : #scope)"
    )
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
    @Cacheable(
            cacheNames = CacheNames.CATEGORIES,
            key = "'active:' + (#scope == null ? 'any' : #scope)"
    )
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


    /**
     * Hard delete, but only for a category nothing has ever used — one created
     * by mistake, or renamed into existence and abandoned.
     *
     * <p>A category that content points at is not deleted, it is retired:
     * {@code PATCH /api/v1/categories/{id}} with {@code isActive: false} hides
     * it from every "choose a category" list while the problems and showcases
     * already filed under it keep reading correctly. Deleting instead used to
     * do one of two things depending on which table happened to carry a
     * foreign key — refuse for showcases, and for problems succeed while
     * leaving every one of them pointing at a category that no longer exists,
     * unable to be re-submitted and rendering with no category at all.
     */
    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.CATEGORIES, allEntries = true)
    public void deleteCategory(UUID id) {

        if (!AuthUtils.hasRole("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ADMIN can delete categories");
        }

        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Category not found with this uuid");
        }

        requireUnused(id);

        try {
            categoryRepository.deleteById(id);
            // Forced now so that a foreign key raised by content created
            // between the count above and this line is still catchable here.
            // The count is a courtesy that explains the refusal; this is what
            // actually guarantees it.
            categoryRepository.flush();
        } catch (DataIntegrityViolationException stillInUse) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Category is now in use and can no longer be deleted. "
                            + "Deactivate it instead."
            );
        }
    }

    /**
     * Counts every reference, soft-deleted rows included — those still hold
     * the category id, so the database would refuse the delete regardless of
     * what a friendlier count claimed.
     */
    private void requireUnused(UUID id) {

        long problems = problemRepository.countByCategoryId(id);
        long showcases = showCasesRepository.countByCategory_Id(id);
        long revisions = showcaseRevisionRepository.countByCategory_Id(id);

        long total = problems + showcases + revisions;
        if (total == 0) {
            return;
        }

        List<String> usage = new java.util.ArrayList<>();
        if (problems > 0) {
            usage.add(problems + " problem" + (problems == 1 ? "" : "s"));
        }
        if (showcases > 0) {
            usage.add(showcases + " showcase" + (showcases == 1 ? "" : "s"));
        }
        if (revisions > 0) {
            usage.add(revisions
                    + " showcase revision" + (revisions == 1 ? "" : "s"));
        }

        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Category is used by " + String.join(", ", usage)
                        + " and cannot be deleted. Deactivate it instead by "
                        + "patching isActive to false, which hides it from new "
                        + "content and leaves existing content intact."
        );
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
    @CacheEvict(cacheNames = CacheNames.CATEGORIES, allEntries = true)
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
    @CacheEvict(cacheNames = CacheNames.CATEGORIES, allEntries = true)
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
    @CacheEvict(cacheNames = CacheNames.CATEGORIES, allEntries = true)
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
