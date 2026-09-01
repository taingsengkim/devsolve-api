package kh.edu.istad.ite.devsoleapi.feature.userprofile.dto;

import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.GenderStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

@Builder
public record UserProfileResponse(
        UUID id,
        String username,
        /**
         * When the handle may next be changed, or null when it may be changed
         * now. Sent so an edit form can disable the field and say why, rather
         * than letting someone type a new handle and be refused on submit.
         */
        LocalDateTime usernameChangeableAt,
        String email,
        String firstName,
        String lastName,
        String fullName,
        String biography,
        String phone,
        String avatarUrl,
        String coverImageUrl,
        LocalDate dateOfBirth,
        GenderStatus gender,
        String country,
        List<SocialLinkResponse> socialLinks,
        UserStatus status,
        int reputation,
        int totalReports,
        int validReports,
        int criticalReports,
        int recognitionCount,
        /**
         * Total paid to this researcher across every program, and how many of
         * their reports carried a payout. Computed from the rewards
         * themselves, so a corrected or withdrawn payout is reflected
         * immediately. Zero rather than null when they have never been paid.
         */
        BigDecimal totalBountyEarned,
        String bountyCurrency,
        long rewardedReports,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
