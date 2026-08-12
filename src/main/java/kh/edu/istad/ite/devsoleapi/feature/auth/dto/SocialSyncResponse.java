package kh.edu.istad.ite.devsoleapi.feature.auth.dto;

import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.UserProfileResponse;

/**
 * Result of {@code POST /api/v1/auth/social/sync}.
 *
 * @param created {@code true} only when this call wrote the profile row, so the
 *                client can send a first-time user to onboarding without
 *                needing a second request to tell a new account from a returning
 *                one.
 * @param profile the local profile, identical to {@code GET
 *                /api/v1/user-profiles/me}, so the client can populate its
 *                session from this one response.
 */
public record SocialSyncResponse(
        boolean created,
        UserProfileResponse profile
) {
}
