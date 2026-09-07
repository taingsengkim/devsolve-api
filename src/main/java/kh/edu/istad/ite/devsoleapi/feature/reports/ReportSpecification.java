package kh.edu.istad.ite.devsoleapi.feature.reports;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class ReportSpecification {

    /**
     * The character LIKE treats as an escape in {@link #matching}. A report
     * title can legitimately contain an underscore or a percent sign, and both
     * are wildcards to LIKE — unescaped, a search for {@code a_b} quietly
     * matches {@code axb} too.
     */
    private static final char LIKE_ESCAPE = '\\';

    private ReportSpecification() {
    }

    public static Specification<Report> forProgram(UUID programId) {
        return (root, query, criteriaBuilder) -> programId == null
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.equal(
                        root.get("program").get("id"),
                        programId
                );
    }

    public static Specification<Report> withState(ReportState state) {
        return (root, query, criteriaBuilder) -> state == null
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.equal(root.get("state"), state);
    }

    /**
     * Reports whose <em>effective</em> severity is the one asked for — the
     * settled rating if there is one, else what triage rated it, else what the
     * reporter claimed.
     *
     * <p>Filtering on the {@code severity} column alone would be the obvious
     * reading and close to useless: that column is null until the two sides
     * agree, so "show me my criticals" would hide every finding still in triage
     * — exactly the ones a researcher is watching. This matches instead on the
     * rating the response actually renders, so the filter agrees with the list
     * it filters.
     *
     * <p>Spelled as a chain of comparisons rather than {@code COALESCE} because
     * all three are Postgres {@code severity_enum} columns, and an equality
     * against a mapped path is the form Hibernate binds an enum parameter for
     * without a cast.
     */
    public static Specification<Report> withEffectiveSeverity(
            Severity severity
    ) {
        return (root, query, criteriaBuilder) -> {
            if (severity == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.or(
                    criteriaBuilder.equal(root.get("severity"), severity),
                    criteriaBuilder.and(
                            criteriaBuilder.isNull(root.get("severity")),
                            criteriaBuilder.equal(
                                    root.get("triageSeverity"),
                                    severity
                            )
                    ),
                    criteriaBuilder.and(
                            criteriaBuilder.isNull(root.get("severity")),
                            criteriaBuilder.isNull(root.get("triageSeverity")),
                            criteriaBuilder.equal(
                                    root.get("reportedSeverity"),
                                    severity
                            )
                    )
            );
        };
    }

    public static Specification<Report> submittedBy(UUID reporterId) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(
                        root.get("reporter").get("id"),
                        reporterId
                );
    }

    public static Specification<Report> forOrganizations(
            Set<UUID> organizationIds
    ) {
        return (root, query, criteriaBuilder) -> {
            if (organizationIds.isEmpty()) {
                return criteriaBuilder.disjunction();
            }
            return root.get("program")
                    .get("organizationId")
                    .in(organizationIds);
        };
    }

    /**
     * Free text over what somebody scanning their own reports would type: the
     * title, the finding itself, and the program it was filed against.
     *
     * <p>Always applied on top of the caller's own scope — their reports, or
     * their organization's — so the match is over rows they can already read.
     * It is not a search endpoint: no ranking, no stemming, just the substring,
     * which is what a filter box on a list of tens of rows is.
     */
    public static Specification<Report> matching(String search) {
        String pattern = likePattern(search);
        return (root, query, criteriaBuilder) -> {
            if (pattern == null) {
                return criteriaBuilder.conjunction();
            }
            // Inner, because a report cannot exist without its program.
            Join<Object, Object> program =
                    root.join("program", JoinType.INNER);
            return criteriaBuilder.or(
                    like(criteriaBuilder, root.get("title"), pattern),
                    like(
                            criteriaBuilder,
                            root.get("vulnerabilityInformation"),
                            pattern
                    ),
                    like(criteriaBuilder, program.get("name"), pattern),
                    like(criteriaBuilder, program.get("handle"), pattern),
                    reference(criteriaBuilder, root, search)
            );
        };
    }

    /**
     * The {@code RPT-XXXXXXXX} handle a researcher reads off their own queue
     * and pastes back into the filter box.
     *
     * <p>Derived from the id rather than stored, so it cannot be matched with a
     * LIKE. What it can be is un-derived: the eight characters are the opening
     * of the UUID, so a term that looks like one narrows to the reports whose
     * id starts with it. Anything else — and any term that is not the whole
     * eight characters — contributes nothing and leaves the text matches above
     * to answer.
     */
    private static Predicate reference(
            CriteriaBuilder criteriaBuilder,
            Root<Report> root,
            String search
    ) {
        String term = search.strip().toLowerCase(Locale.ROOT);
        if (term.startsWith("rpt-")) {
            term = term.substring("rpt-".length());
        }
        if (!term.matches("[0-9a-f]{8}")) {
            return criteriaBuilder.disjunction();
        }
        // A UUID is fixed-width and its first eight characters are its first
        // four bytes, so the prefix bounds a contiguous range: everything from
        // …-0000-… to …-ffff-…. Expressed as a BETWEEN so the id index can
        // serve it, rather than casting every row to text.
        return criteriaBuilder.between(
                root.<UUID>get("id"),
                UUID.fromString(term + "-0000-0000-0000-000000000000"),
                UUID.fromString(term + "-ffff-ffff-ffff-ffffffffffff")
        );
    }

    private static Predicate like(
            CriteriaBuilder criteriaBuilder,
            Expression<String> field,
            String pattern
    ) {
        return criteriaBuilder.like(
                criteriaBuilder.lower(field),
                pattern,
                LIKE_ESCAPE
        );
    }

    /**
     * The search term as a LIKE pattern, or null when there is nothing to
     * search for. Blank is treated as absent so that clearing a filter box does
     * not send a pattern that matches everything the long way round.
     */
    private static String likePattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String escaped = search.strip()
                .toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
