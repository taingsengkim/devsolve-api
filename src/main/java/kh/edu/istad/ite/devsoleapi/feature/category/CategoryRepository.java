package kh.edu.istad.ite.devsoleapi.feature.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    Optional<Category> findBySlug(String slug);

    List<Category> findAllBySlug(String slug);

    boolean existsBySlug(String slug);

    Optional<Category> findByIdAndScopeAndIsActiveTrue(
            UUID id,
            CategoryScope scope
    );

    Optional<Category> findByScopeAndSlug(CategoryScope scope, String slug);

    boolean existsByScopeAndSlug(CategoryScope scope, String slug);

    List<Category> findAllByScope(CategoryScope scope);

    List<Category> findByIsActiveTrueOrderBySortOrderAsc();

    List<Category> findByScopeAndIsActiveTrueOrderBySortOrderAsc(
            CategoryScope scope
    );

    boolean existsBySlugAndIdNot(String slug, UUID id);

    boolean existsByScopeAndSlugAndIdNot(
            CategoryScope scope,
            String slug,
            UUID id
    );
}
