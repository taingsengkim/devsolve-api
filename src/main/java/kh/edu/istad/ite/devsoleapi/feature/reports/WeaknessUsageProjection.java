package kh.edu.istad.ite.devsoleapi.feature.reports;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A catalog entry with how much it is actually used.
 *
 * <p>The reason the weakness field is a closed vocabulary rather than free text
 * is that it aggregates; this is the aggregate. Read straight out of the group
 * by, so a catalog of any size costs one query rather than one count per entry.
 */
public interface WeaknessUsageProjection {

    UUID getId();

    String getCweId();

    String getName();

    Boolean getIsActive();

    /**
     * Every report filed under this class, whatever became of it — duplicates
     * and rejections included. This is "how often do we see this", not "how
     * often were we right about it", and dropping the ones that went nowhere
     * would quietly answer a different question.
     */
    long getReportCount();

    /**
     * The subset a triager agreed with: confirmed, being retested, or resolved.
     * Read next to {@link #getReportCount()} it separates a class that arrives
     * constantly and holds up from one that arrives constantly and does not.
     */
    long getValidCount();

    /** Null on an entry nothing has ever been filed under. */
    LocalDateTime getLastReportedAt();
}
