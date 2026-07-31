package kh.edu.istad.ite.devsoleapi.feature.vote;

public interface VoteSummaryProjection {

    long getScore();

    long getUpvotes();

    long getDownvotes();
}
