package kh.edu.istad.ite.devsoleapi.feature.reports.dto;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.AssetType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisclosureStatus;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisputeStatus;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportEnvironment;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.RetestVerdict;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @param reputationPoints what resolving this finding earned its reporter,
 *                         priced by severity by the platform rather than by
 *                         the organization. Null until the report is resolved,
 *                         and on reports resolved before reputation was paid on
 *                         resolution. Zero on an informational finding, which
 *                         is credited but does not move the leaderboard.
 */
public record ReportResponse(
        UUID id,

        /**
         * The short handle a triager reads out loud, derived from the id rather
         * than stored: {@code RPT-} and the first eight characters of the UUID.
         * Unique in practice and stable, but not guaranteed unique by the
         * database — address the report by {@link #id}, show this to people.
         */
        String reportId,

        UUID programId,
        UUID reporterId,

        /**
         * Who filed it. The bare {@code reporterId} above is kept beside this
         * so existing clients keep working; new ones should read this.
         */
        ResearcherSummary researcher,

        /** What it was filed against, with the company that runs it. */
        ProgramSummary program,

        String title,

        /**
         * The opening of {@link #vulnerabilityInformation} on one line, for a
         * queue that has no room for the whole thing. Derived, not stored.
         */
        String summary,

        String vulnerabilityInformation,
        String impact,
        String stepsToReproduce,
        String proofOfConcept,
        String remediationRecommendation,
        String targetEndpoint,
        ReportEnvironment environment,
        LocalDateTime discoveredAt,
        List<String> referenceLinks,
        Severity reportedSeverity,
        String cvssVector,
        BigDecimal cvssScore,
        Severity triageSeverity,
        Severity severity,
        WeaknessSummary weakness,

        /**
         * What the reporter called the class when the catalog did not have it.
         * Null whenever {@code weakness} is set — at most one of the two is
         * ever populated, and both null means nobody has classified the report
         * yet.
         */
        String suggestedWeakness,

        AssetSummary asset,
        ReportState state,

        /**
         * Whether the program pays. Read from the program rather than stored on
         * the report, so a program that changes how it engages does not leave a
         * queue full of findings claiming otherwise.
         */
        EngagementType type,

        DisclosureStatus disclosureStatus,
        UUID duplicateOfId,
        UUID triagedBy,
        DisputeSummary dispute,

        /**
         * Whether a severity dispute is open on this report right now — not
         * whether one was ever raised. A queue colours a row on this, and a
         * finding whose disagreement was settled last week is not in dispute.
         */
        boolean isDisputed,
        List<AttachmentSummary> attachments,
        List<RewardSummary> rewards,
        List<RetestSummary> retestHistory,
        LocalDateTime submittedAt,
        LocalDateTime triagedAt,

        /**
         * The first time anybody but the reporter acted on the report. Null
         * while it is still waiting, and on reports that predate the column.
         * Unlike {@code triagedAt} this is never overwritten, so it is the one
         * that answers how long the reporter actually waited.
         */
        LocalDateTime firstRespondedAt,

        LocalDateTime resolvedAt,
        Integer reputationPoints,
        LocalDateTime reputationAwardedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * The researcher who filed it, as a triager needs to weigh it.
     *
     * <p>The counters are context for a human — a first report from a new
     * account reads differently from the twenty-fourth valid one — and not a
     * score anything acts on.
     *
     * <p>{@code email} is here because a company that owes a payout, or needs
     * to ask a question off-platform, has no other way to reach the person.
     * Everybody who can read this response can already read the report itself:
     * the reporter, the company's members holding VIEW_REPORTS, and platform
     * administrators. It goes no further than that.
     */
    public record ResearcherSummary(
            UUID id,
            String username,
            String fullName,
            String email,
            String avatarUrl,
            int reputation,
            int totalReports,
            int validReports,
            String country
    ) {
    }

    /**
     * @param organizationName    null on a report whose organization row has
     *                            since been deleted. The report outlives it,
     *                            and a triage queue that dropped those rows
     *                            would hide exactly the findings nobody owns
     * @param organizationSlug    what a link to the company profile is built
     *                            from, and null on the same deleted row as the
     *                            name. Carried so a queue can link a company
     *                            without resolving the id to a slug per row
     */
    public record ProgramSummary(
            UUID id,
            String name,
            String handle,
            UUID organizationId,
            String organizationName,
            String organizationSlug,
            String organizationLogoUrl
    ) {
    }

    public record WeaknessSummary(
            UUID id,
            String cweId,
            String name
    ) {
    }

    public record AssetSummary(
            UUID id,
            AssetType assetType,
            String identifier,
            Boolean isInScope,
            Severity maxSeverity
    ) {
    }

    public record AttachmentSummary(
            UUID id,
            String fileName,
            String downloadUrl,
            String mimeType,
            Long sizeBytes,
            UUID uploadedBy,
            LocalDateTime createdAt
    ) {
    }

    public record RewardSummary(
            UUID id,
            BigDecimal amount,
            Integer points,
            UUID awardedBy,
            LocalDateTime awardedAt,
            String note
    ) {
    }

    /**
     * One round of fix verification, oldest attempt first.
     *
     * <p>{@code completedAt} is what says whether the attempt is still open;
     * an attempt closed without a {@code verdict} is one triage moved the
     * report on from, or one whose {@code dueAt} passed unanswered.
     *
     * @param dueAt when the researcher's window to answer runs out. Null on
     *              attempts that predate the window.
     */
    public record RetestSummary(
            UUID id,
            int attemptNumber,
            ReportEnvironment environment,
            String targetEndpoint,
            LocalDateTime requestedAt,
            LocalDateTime dueAt,
            ActorSummary requestedBy,
            String requestNotes,
            BigDecimal bountyReward,
            LocalDateTime completedAt,
            ActorSummary completedBy,
            RetestVerdict verdict,
            String resultNotes,
            List<UUID> attachmentIds
    ) {
    }

    /**
     * Who did something, named. The rest of this response carries bare ids,
     * but a retest history is read as a chronology of people acting on the
     * report, and resolving every id to a name client-side to render it is a
     * request per row.
     */
    public record ActorSummary(
            UUID id,
            String name
    ) {
    }

    /**
     * @param respondBy when the reporter's window to accept or refuse a triage
     *                  severity closes. Set only while {@code status} is
     *                  AWAITING_REPORTER; null everywhere else, including on
     *                  disputes raised before that step existed. Silence past
     *                  it settles at the triage severity.
     */
    public record DisputeSummary(
            UUID id,
            DisputeStatus status,
            UUID raisedBy,
            String reason,
            Severity resolvedSeverity,
            UUID resolvedBy,
            String resolutionNotes,
            LocalDateTime createdAt,
            LocalDateTime resolvedAt,
            LocalDateTime respondBy
    ) {
    }
}
