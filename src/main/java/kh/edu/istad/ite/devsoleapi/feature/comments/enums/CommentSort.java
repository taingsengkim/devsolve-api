package kh.edu.istad.ite.devsoleapi.feature.comments.enums;

/**
 * How a caller wants a page of comments ordered.
 *
 * <p>{@code NEWEST} stays the default for root comments and {@code OLDEST} for
 * replies, which is what the API did before this existed: a discussion reads
 * top-down within a thread, but the threads themselves read newest-first.
 */
public enum CommentSort {

    NEWEST,

    OLDEST,

    /**
     * Highest vote score first, ties broken by recency. Scores live in the
     * votes table, so this is the one ordering the comment queries cannot
     * express on their own.
     */
    TOP
}
