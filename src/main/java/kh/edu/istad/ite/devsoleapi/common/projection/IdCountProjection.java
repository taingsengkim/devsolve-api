package kh.edu.istad.ite.devsoleapi.common.projection;

import java.util.UUID;

/**
 * One row of a {@code group by} aggregate keyed by entity id, so a listing can
 * total up counts for a whole page in a single query instead of one query per
 * row. Queries feeding this must alias their columns {@code id} and
 * {@code total}.
 *
 * <p>Ids absent from the result have no matching rows at all; callers read
 * them as zero rather than expecting an entry.
 */
public interface IdCountProjection {

    UUID getId();

    long getTotal();
}
