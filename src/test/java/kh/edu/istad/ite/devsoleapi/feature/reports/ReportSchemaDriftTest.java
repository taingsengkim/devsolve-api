package kh.edu.istad.ite.devsoleapi.feature.reports;

import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Transient;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.ReportRetest;
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

    @Test
    void everyReportColumnIsEitherOriginalOrAddedBySchemaSql()
            throws IOException {

        assertNoDrift("reports", Report.class, ORIGINAL_COLUMNS);
    }

    /**
     * The same trap one table over. {@code report_retests} is created by
     * schema.sql, but with {@code CREATE TABLE IF NOT EXISTS} — so on every
     * database that already ran the release which introduced it, the CREATE is
     * skipped and a column added to {@link ReportRetest} afterwards arrives
     * only if an ALTER block carries it. A fresh database gets the column from
     * the CREATE and shows nothing wrong.
     */
    @Test
    void everyRetestColumnIsEitherCreatedOrAddedBySchemaSql()
            throws IOException {

        assertNoDrift(
                "report_retests",
                ReportRetest.class,
                createdColumnsOf("report_retests")
        );
    }

    private void assertNoDrift(
            String table,
            Class<?> entity,
            Set<String> originalColumns
    ) throws IOException {

        Set<String> guaranteed = new java.util.HashSet<>(originalColumns);
        Matcher matcher = addedColumnPattern(table).matcher(schemaSql());

        while (matcher.find()) {
            guaranteed.add(matcher.group(1).toLowerCase());
        }

        List<String> drifted = new ArrayList<>();

        for (String column : mappedColumnsOf(entity)) {
            if (!guaranteed.contains(column)) {
                drifted.add(column);
            }
        }

        assertTrue(drifted.isEmpty(), () ->
                table + "."
                        + String.join(", " + table + ".", drifted)
                        + " is mapped by the " + entity.getSimpleName()
                        + " entity but schema.sql neither creates nor adds it. "
                        + "A deploy ships the field with no column to put it "
                        + "in, and every read of the table fails — see this "
                        + "test's javadoc. Add an ALTER TABLE public." + table
                        + " ADD COLUMN IF NOT EXISTS block for it."
        );
    }

    private Pattern addedColumnPattern(String table) {
        return Pattern.compile(
                "ALTER TABLE public\\." + table
                        + "\\s+ADD COLUMN IF NOT EXISTS\\s+(\\w+)",
                Pattern.CASE_INSENSITIVE
        );
    }

    /**
     * The column names inside a table's {@code CREATE TABLE} block: the ones a
     * fresh database is guaranteed, and — because the table and its entity
     * shipped together — the ones every older database already has too.
     *
     * <p>Read from the first line of each definition, which is why constraint
     * and reference continuation lines are skipped rather than parsed.
     */
    private Set<String> createdColumnsOf(String table) throws IOException {
        Matcher block = Pattern.compile(
                "CREATE TABLE IF NOT EXISTS public\\." + table
                        + "\\s*\\((.*?)\\n\\s*\\);",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        ).matcher(schemaSql());

        if (!block.find()) {
            throw new IllegalStateException(
                    "schema.sql has no CREATE TABLE block for " + table
            );
        }

        Set<String> columns = new java.util.HashSet<>();
        for (String line : block.group(1).split("\\R")) {
            Matcher name = Pattern.compile("^\\s{12}(\\w+)\\s+\\S")
                    .matcher(line);
            if (name.find()
                    && !name.group(1).equalsIgnoreCase("CONSTRAINT")) {
                columns.add(name.group(1).toLowerCase());
            }
        }
        return columns;
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
