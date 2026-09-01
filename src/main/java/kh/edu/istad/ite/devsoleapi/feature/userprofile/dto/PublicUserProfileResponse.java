package kh.edu.istad.ite.devsoleapi.feature.userprofile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

/**
 * Somebody's profile as anyone may see it.
 *
 * @param email null unless the caller is a colleague — signed in, and at an
 *              active organization this person is also at. The team roster
 *              already shows that same viewer this address, so withholding it
 *              here was inconsistent rather than protective; withholding it
 *              from everyone else still is the point.
 */
public record PublicUserProfileResponse(
        UUID id,
        String username,
        String fullName,

        @Schema(
                nullable = true,
                description = "Present only when the caller shares an active "
                        + "organization with this person, or is this person."
        )
        String email,

        String biography,
        String avatarUrl,
        String coverImageUrl,
        String country,
        List<SocialLinkResponse> socialLinks,
        int reputation,
        int totalReports,
        int validReports,
        int criticalReports,
        int recognitionCount,
        /**
         * Total paid to this researcher across every program, and how many of
         * their reports carried a payout. Public on purpose: earnings are the
         * headline figure of a researcher's track record, and the hacktivity
         * feed already publishes each individual bounty.
         */
        BigDecimal totalBountyEarned,
        String bountyCurrency,
        long rewardedReports,
        LocalDateTime joinedAt
) {
}
