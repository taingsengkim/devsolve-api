package kh.edu.istad.ite.devsoleapi.feature.reports;

import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Transient;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the failure that took the public hacktivity feed down.
 *
 * <p>schema.sql never creates {@code public.reports}. It only alters it, so on
 * any database older than {@code ddl-auto=update} being switched off, the
 * table is whatever Hibernate built back then plus whatever those ALTER blocks
 * have added since. A field added to {@link Report} and not listed in one of
 * them therefore exists on a fresh database and on nobody's production.
 *
 * <p>The consequence is not a missing value. Hibernate names every column of
 * an entity in its SELECT, so one absent column fails the whole statement —
 * every read of a Report 500s, rows or no rows, and takes down the endpoints
 * that only touch Reports through a join. Four columns had drifted this way:
 * {@code disclosure_status}, {@code triaged_by}, {@code triaged_at} and
 * {@code duplicate_of_id}.
 *
 * <p>This test runs nowhere near a database on purpose. The drift is invisible
 * to any test whose schema Hibernate generated, because that schema is the
 * entity by definition — which is exactly why it reached production.
 */
class ReportSchemaDriftTest {

    /**
     * Columns Hibernate created with the table, before schema.sql took over
     * maintaining it. Adding to this list asserts that a column already exists
     * on every deployed database — if that is not true of the one you are
     * adding, it belongs in an ALTER block instead.
     */
    private static final Set<String> ORIGINAL_COLUMNS = Set.of(
            "id",
            "program_id",
            "reporter_id",
            "title",
            "vulnerability_information",
            "impact",
            "reported_severity",
            "state",
            "submitted_at",
            "resolved_at",
            "created_at",
            "updated_at"
    );

    private static final Pattern ADDED_COLUMN = Pattern.compile(
            "ALTER TABLE public\\.reports\\s+ADD COLUMN IF NOT EXISTS\\s+"
                    + "(\\w+)",
            Pattern.CASE_INSENSITIVE
    );

    @Test
    void everyReportColumnIsEitherOriginalOrAddedBySchemaSql()
            throws IOException {

        Set<String> guaranteed = new java.util.HashSet<>(ORIGINAL_COLUMNS);
        Matcher matcher = ADDED_COLUMN.matcher(schemaSql());

        while (matcher.find()) {
            guaranteed.add(matcher.group(1).toLowerCase());
        }

        List<String> drifted = new ArrayList<>();

        for (String column : mappedColumnsOf(Report.class)) {
            if (!guaranteed.contains(column)) {
                drifted.add(column);
            }
        }

        assertTrue(drifted.isEmpty(), () ->
                "reports."
                        + String.join(", reports.", drifted)
                        + " is mapped by the Report entity but schema.sql "
                        + "neither creates nor adds it. A deploy ships the "
                        + "field with no column to put it in, and every read "
                        + "of a Report fails — see this test's javadoc. Add an "
                        + "ALTER TABLE public.reports ADD COLUMN IF NOT EXISTS "
                        + "block for it."
        );
    }

    /**
     * The column each persistent field maps to. Collections are skipped —
     * they live in the child table, not this one — and so is anything the
     * compiler or Lombok added, which is what the synthetic check is for.
     */
    private List<String> mappedColumnsOf(Class<?> entity) {

        List<String> columns = new ArrayList<>();

        for (Field field : entity.getDeclaredFields()) {

            if (field.isSynthetic()
                    || field.isAnnotationPresent(Transient.class)
                    || field.isAnnotationPresent(OneToMany.class)) {
                continue;
            }

            JoinColumn join = field.getAnnotation(JoinColumn.class);
            if (join != null && !join.name().isEmpty()) {
                columns.add(join.name().toLowerCase());
                continue;
            }

            Column column = field.getAnnotation(Column.class);
            if (column != null && !column.name().isEmpty()) {
                columns.add(column.name().toLowerCase());
                continue;
            }

            columns.add(camelToSnake(field.getName()));
        }

        return columns;
    }

    private String camelToSnake(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    private String schemaSql() throws IOException {
        try (InputStream schema = getClass()
                .getClassLoader()
                .getResourceAsStream("schema.sql")) {

            if (schema == null) {
                throw new IllegalStateException("schema.sql is not on the "
                        + "test classpath");
            }

            return new String(
                    schema.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }
    }
}
