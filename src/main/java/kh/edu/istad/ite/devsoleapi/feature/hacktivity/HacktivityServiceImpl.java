package kh.edu.istad.ite.devsoleapi.feature.hacktivity;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import kh.edu.istad.ite.devsoleapi.feature.hacktivity.dto.HacktivityFilter;
import kh.edu.istad.ite.devsoleapi.feature.hacktivity.dto.HacktivityResponse;
import kh.edu.istad.ite.devsoleapi.feature.hacktivity.dto.HacktivityStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HacktivityServiceImpl implements HacktivityService {

    /**
     * The character LIKE treats as an escape below. A researcher handle can
     * legitimately contain an underscore, which is a single-character wildcard
     * to LIKE — unescaped, a search for {@code a_b} quietly matches
     * {@code axb} too.
     */
    private static final char LIKE_ESCAPE = '\\';

    private final HacktivityRepository hacktivityRepository;
    private final HacktivityMapper hacktivityMapper;

    @Override
    public Page<HacktivityResponse> search(
            HacktivityFilter filter,
            Pageable pageable
    ) {

        Page<Hacktivity> page = hacktivityRepository.findAll(
                specificationFor(filter),
                pageable
        );

        if (page.isEmpty()) {
            return page.map(hacktivity ->
                    hacktivityMapper.toResponse(hacktivity, null));
        }

        // Pulls this page's five associations in one query. The rows come
        // back as the same managed instances the page is already holding, so
        // the return value is deliberately unused — hydrating them is the
        // whole point of the call.
        hacktivityRepository.findAllWithAssociations(
                page.getContent().stream()
                        .map(Hacktivity::getId)
                        .toList()
        );

        Map<UUID, HacktivityRepository.ReportPayout> payouts =
                payoutsFor(page.getContent());

        return page.map(hacktivity -> hacktivityMapper.toResponse(
                hacktivity,
                payouts.get(hacktivity.getReport().getId())
        ));
    }

    @Override
    public HacktivityStatsResponse getStats() {

        HacktivityRepository.FeedTotals totals =
                hacktivityRepository.findFeedTotals();

        BigDecimal paid = hacktivityRepository.sumPaidOut();

        return new HacktivityStatsResponse(
                totals.getEntries(),
                totals.getResearchers(),
                totals.getPrograms(),
                paid == null ? BigDecimal.ZERO : paid,
                HacktivityMapper.CURRENCY
        );
    }

    private Map<UUID, HacktivityRepository.ReportPayout> payoutsFor(
            List<Hacktivity> rows
    ) {

        // Distinct because two recognitions can point at one report, and a
        // duplicate key is a collector failure rather than a merge.
        List<UUID> reportIds = rows.stream()
                .map(hacktivity -> hacktivity.getReport().getId())
                .distinct()
                .toList();

        return hacktivityRepository.findPayoutsByReportIds(reportIds)
                .stream()
                .collect(Collectors.toMap(
                        HacktivityRepository.ReportPayout::getReportId,
                        Function.identity()
                ));
    }

    /**
     * The filters, as one predicate over the root.
     *
     * <p>No fetch joins here on purpose: this specification is also what the
     * count query behind {@code Page} is built from, and a count query is the
     * one place a fetch join is illegal. The joins it does make are all
     * to-one, so neither the count nor the page can be multiplied by them.
     */
    private Specification<Hacktivity> specificationFor(
            HacktivityFilter filter
    ) {

        return (root, query, builder) -> {

            List<Predicate> predicates = new ArrayList<>();

            if (filter.userId() != null) {
                predicates.add(builder.equal(
                        root.get("user").get("id"), filter.userId()
                ));
            }

            if (filter.organizationId() != null) {
                predicates.add(builder.equal(
                        root.get("organization").get("id"),
                        filter.organizationId()
                ));
            }

            if (filter.programId() != null) {
                predicates.add(builder.equal(
                        root.get("program").get("id"), filter.programId()
                ));
            }

            if (filter.severities() != null
                    && !filter.severities().isEmpty()) {
                predicates.add(
                        root.get("report").get("severity")
                                .in(filter.severities())
                );
            }

            if (filter.eventTypes() != null
                    && !filter.eventTypes().isEmpty()) {
                predicates.add(
                        root.get("eventType").in(filter.eventTypes())
                );
            }

            String term = searchTerm(filter.q());

            if (term != null) {
                Join<Object, Object> user = root.join("user");
                Join<Object, Object> program = root.join("program");
                Join<Object, Object> report = root.join("report");

                predicates.add(builder.or(
                        like(builder, user.get("username"), term),
                        like(builder, user.get("fullName"), term),
                        like(builder, program.get("name"), term),
                        like(builder, report.get("title"), term)
                ));
            }

            return predicates.isEmpty()
                    ? null
                    : builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Predicate like(
            jakarta.persistence.criteria.CriteriaBuilder builder,
            jakarta.persistence.criteria.Expression<String> field,
            String term
    ) {
        return builder.like(builder.lower(field), term, LIKE_ESCAPE);
    }

    /**
     * The search term as a LIKE pattern, or null when there is nothing to
     * search for. Blank is treated as absent so that clearing a search box
     * does not send a filter that matches everything the long way round.
     */
    private String searchTerm(String q) {

        if (q == null || q.isBlank()) {
            return null;
        }

        String escaped = q.strip()
                .toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");

        return "%" + escaped + "%";
    }
}
