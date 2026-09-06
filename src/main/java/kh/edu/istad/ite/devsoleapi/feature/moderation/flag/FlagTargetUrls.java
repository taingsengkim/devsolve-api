package kh.edu.istad.ite.devsoleapi.feature.moderation.flag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Where a moderator goes to read the thing that was reported.
 *
 * <p>Frontend routes in the API, which is a coupling worth naming. The
 * alternative is every client rebuilding the same five-way switch — including
 * the two cases nobody guesses right, a solution having no page of its own and
 * a comment living wherever the thing it replies to lives — and getting the
 * moderation queue wrong is how a report gets closed against the wrong post.
 * The paths are configurable so a route change is a deployment setting rather
 * than a release.
 *
 * <p>Relative to the site root. An absolute URL would bake the environment into
 * a response the frontend is going to navigate itself anyway.
 */
@Component
public class FlagTargetUrls {

    private final String problemPath;
    private final String showcasePath;
    private final String programPath;
    private final String reportPath;

    public FlagTargetUrls(
            @Value("${app.frontend.problem-path:/community/problems}")
            String problemPath,
            @Value("${app.frontend.showcase-path:/community/showcases}")
            String showcasePath,
            @Value("${app.frontend.program-path:/programs}")
            String programPath,
            @Value("${app.frontend.report-path:/reports}")
            String reportPath
    ) {
        this.problemPath = trimTrailingSlash(problemPath);
        this.showcasePath = trimTrailingSlash(showcasePath);
        this.programPath = trimTrailingSlash(programPath);
        this.reportPath = trimTrailingSlash(reportPath);
    }

    /**
     * @param slug       a program's handle, which is how a program is
     *                   addressed; null for everything else
     * @param parentType what a comment or solution hangs off, already
     *                   normalised to upper case by the query
     * @param parentId   that parent's ID
     * @return null when there is nowhere to send them — the content is gone, or
     *         it hangs off something this does not know how to address. A null
     *         link renders as no link; a wrong one sends a moderator to the
     *         wrong page and lets them act on it
     */
    public String directUrl(
            FlaggableType type,
            UUID id,
            String slug,
            String parentType,
            UUID parentId
    ) {
        if (type == null || id == null) {
            return null;
        }
        return switch (type) {
            case PROBLEM -> problemPath + "/" + id;
            case SHOWCASE -> showcasePath + "/" + id;
            case PROGRAM -> programPath + "/"
                    + (slug == null || slug.isBlank() ? id : slug);

            // A solution is read on its problem's page, anchored. Without the
            // problem there is no page at all, which is what a solution whose
            // parent has been hard-deleted looks like.
            case SOLUTION -> parentId == null
                    ? null
                    : problemPath + "/" + parentId + "#solution-" + id;

            case COMMENT -> commentUrl(id, parentType, parentId);
        };
    }

    /**
     * A comment has no page. It is read where it was written, so the link is to
     * whatever it hangs off with the comment anchored on it.
     *
     * <p>The query has already turned a comment on a solution into a comment on
     * that solution's problem, since a solution has no page either.
     */
    private String commentUrl(UUID id, String parentType, UUID parentId) {
        if (parentType == null || parentId == null) {
            return null;
        }
        String anchor = "#comment-" + id;
        return switch (parentType) {
            case "PROBLEM" -> problemPath + "/" + parentId + anchor;
            case "SHOWCASE" -> showcasePath + "/" + parentId + anchor;
            case "PROGRAM" -> programPath + "/" + parentId + anchor;
            case "REPORT" -> reportPath + "/" + parentId + anchor;

            // A commentable kind added since this was written. Better no link
            // than a guessed one.
            default -> null;
        };
    }

    private static String trimTrailingSlash(String path) {
        if (path == null) {
            return "";
        }
        String trimmed = path.trim();
        return trimmed.endsWith("/")
                ? trimmed.substring(0, trimmed.length() - 1)
                : trimmed;
    }
}
