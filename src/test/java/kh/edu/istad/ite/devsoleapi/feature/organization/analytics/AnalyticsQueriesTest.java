package kh.edu.istad.ite.devsoleapi.feature.organization.analytics;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The analytics SQL is assembled from constants so the scope clause is written
 * once. That saves six copies from drifting and introduces two failure modes a
 * compiler cannot see, both of which only surface against a live database:
 * a fragment spliced in without the whitespace around it, and an alias that
 * stopped matching the projection getter it feeds.
 *
 * <p>So this reads the assembled strings back. It is not a substitute for
 * {@code OrganizationAnalyticsRepositoryPostgresTest}, which runs them — it is
 * what still runs on a machine with no Docker.
 */
class AnalyticsQueriesTest {

    /** Every query has to carry all four halves of the scope. */
    private static final List<String> SCOPE_CLAUSES = List.of(
            "p.organization_id = :organizationId",
            "p.deleted_at IS NULL",
            "CAST(:programId AS uuid) IS NULL",
            "r.submitted_at >= :startDate",
            "r.submitted_at < :endDate"
    );

    /**
     * A keyword welded to the identifier before it. {@code 'resolved'GROUP} is
     * the shape this catches: still SQL, no longer the same query.
     */
    private static final Pattern GLUED_AFTER = Pattern.compile(
            "[A-Za-z0-9_](?=(SELECT|FROM|WHERE|GROUP BY|ORDER BY|LIMIT|JOIN"
                    + "|FILTER|COALESCE|COUNT|CAST)\\b)"
    );

    /**
     * The same weld the other way round: {@code WHEREr.state}.
     *
     * <p>Only keywords no longer word contains. {@code OR} and {@code AND}
     * would flag the {@code OR} at the front of {@code ORDER BY}, which no
     * amount of word-boundary anchoring can tell apart from a real weld.
     */
    private static final Pattern GLUED_BEFORE = Pattern.compile(
            "\\b(SELECT|FROM|WHERE|LIMIT|JOIN)(?=[A-Za-z0-9_])"
    );

    private static final Pattern NAMED_PARAMETER =
            Pattern.compile(":([A-Za-z][A-Za-z0-9_]*)");

    @Test
    void everyQueryCarriesTheWholeScopeClause() {
        forEachQuery((method, sql) ->
                SCOPE_CLAUSES.forEach(clause -> assertTrue(
                        sql.contains(clause),
                        method.getName() + " is missing: " + clause
                ))
        );
    }

    /**
     * The splice points. A fragment that arrived without a space or a newline
     * around it can still parse — {@code 'resolved')GROUP BY} does — and the
     * next one may not.
     */
    @Test
    void noFragmentIsWeldedToTheOneBeforeOrAfterIt() {
        forEachQuery((method, sql) -> {
            assertNoMatch(
                    GLUED_AFTER,
                    sql,
                    method.getName() + " welds a keyword onto an identifier"
            );
            assertNoMatch(
                    GLUED_BEFORE,
                    sql,
                    method.getName() + " welds an identifier onto a keyword"
            );
        });
    }

    /**
     * Postgres folds an unquoted alias to lower case, and a native projection
     * is matched on the alias. Every getter therefore needs a quoted alias
     * spelled exactly as the getter reads.
     */
    @Test
    void everyProjectionGetterHasAnAliasToBindTo() {
        forEachQuery((method, sql) -> {
            Class<?> projection = projectionOf(method);
            for (Method getter : projection.getDeclaredMethods()) {
                String alias = aliasFor(getter);
                assertTrue(
                        sql.contains("AS \"" + alias + "\""),
                        method.getName() + " has no alias for "
                                + projection.getSimpleName() + "."
                                + getter.getName()
                );
            }
        });
    }

    /**
     * A named parameter in the SQL with no {@code @Param} behind it fails at
     * startup; one declared and never used is a filter somebody meant to
     * apply. Both are cheaper to find here.
     */
    @Test
    void theParametersTheSqlUsesAreExactlyTheOnesDeclared() {
        forEachQuery((method, sql) -> assertEquals(
                declaredParameters(method),
                usedParameters(sql),
                method.getName() + " binds a different set of parameters than "
                        + "its SQL uses"
        ));
    }

    /**
     * The one query allowed to name a bucket unit, and the only place a
     * caller-supplied string reaches a function name. It comes from
     * {@link AnalyticsBucket} and nowhere else.
     */
    @Test
    void onlyTheTrendQueryTakesABucketUnit() {
        forEachQuery((method, sql) -> assertEquals(
                method.getName().equals("findSubmissionTrend"),
                sql.contains(":bucket"),
                method.getName()
        ));

        for (AnalyticsBucket bucket : AnalyticsBucket.values()) {
            assertTrue(
                    List.of("day", "week", "month")
                            .contains(bucket.dateTruncUnit()),
                    bucket + " is not a date_trunc unit"
            );
        }
    }

    /**
     * Payouts are folded to one row per report before the join. Joining
     * {@code report_rewards} directly would multiply the report by its
     * payouts, and every count standing beside the sum would double.
     */
    @Test
    void payoutsAreAlwaysPreAggregatedBeforeTheyAreJoined() {
        forEachQuery((method, sql) -> {
            if (!sql.contains("report_rewards")) {
                return;
            }
            assertTrue(
                    sql.contains("GROUP BY reward.report_id"),
                    method.getName() + " joins payouts without folding them "
                            + "to one row per report"
            );
        });
    }

    private void forEachQuery(QueryCheck check) {
        List<Method> queries = Arrays.stream(
                        OrganizationAnalyticsRepository.class
                                .getDeclaredMethods()
                )
                .filter(method -> method.getAnnotation(Query.class) != null)
                .toList();

        assertEquals(6, queries.size(), "unexpected number of analytics queries");
        queries.forEach(method ->
                check.check(method, method.getAnnotation(Query.class).value())
        );
    }

    private Set<String> declaredParameters(Method method) {
        return Arrays.stream(method.getParameters())
                .map(parameter -> parameter.getAnnotation(Param.class))
                .filter(Objects::nonNull)
                .map(Param::value)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> usedParameters(String sql) {
        Matcher matcher = NAMED_PARAMETER.matcher(sql);
        Set<String> names = new LinkedHashSet<>();
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private Class<?> projectionOf(Method method) {
        Type returnType = method.getGenericReturnType();
        if (returnType instanceof ParameterizedType parameterized) {
            return (Class<?>) parameterized.getActualTypeArguments()[0];
        }
        return method.getReturnType();
    }

    private String aliasFor(Method getter) {
        String name = getter.getName().substring("get".length());
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    /**
     * Fails with the offending stretch of SQL rather than with a bare "true
     * was false", because the whole point is to say where the join went wrong.
     */
    private void assertNoMatch(Pattern pattern, String sql, String message) {
        Matcher matcher = pattern.matcher(sql);
        if (!matcher.find()) {
            return;
        }
        int from = Math.max(0, matcher.start() - 30);
        int to = Math.min(sql.length(), matcher.end() + 30);
        fail(message + " near: "
                + sql.substring(from, to).replace("\n", " "));
    }

    @FunctionalInterface
    private interface QueryCheck {

        void check(Method method, String sql);
    }
}
