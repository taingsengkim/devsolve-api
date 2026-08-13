package kh.edu.istad.ite.devsoleapi.feature.vote;

import java.util.UUID;

/**
 * One target's vote tally, keyed by id so a whole page of them can be read in
 * a single query. {@link VoteSummaryProjection} answers the same question for
 * one target at a time; this is the shape a listing needs.
 *
 * <p>Targets nobody has voted on are absent rather than zero-valued — callers
 * default them.
 */
public interface VoteBreakdownProjection {

    UUID getId();

    long getScore();

    long getUpvotes();

    long getDownvotes();
}
