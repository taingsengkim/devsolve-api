package kh.edu.istad.ite.devsoleapi.feature.organization.dto;

import java.math.BigDecimal;

/**
 * The headline numbers on an organization's profile.
 *
 * @param activePrograms  programs currently accepting submissions. Scoped to
 *                        what the caller may see: a public visitor is counted
 *                        only the public ones, so the number always agrees
 *                        with the program list beside it, while the
 *                        organization's own profile counts private and
 *                        invite-only programs too.
 * @param resolvedReports findings the organization has seen through to a fix
 * @param totalDisbursed  every bounty paid, across all programs. Unlike the
 *                        program count this is not scoped by visibility — it
 *                        is the reputational signal researchers judge an
 *                        organization by, and an aggregate reveals no private
 *                        program.
 * @param topBountyAward  the largest single bounty ever paid
 */
public record OrganizationStatsResponse(
        long activePrograms,
        long resolvedReports,
        BigDecimal totalDisbursed,
        BigDecimal topBountyAward
) {
}
