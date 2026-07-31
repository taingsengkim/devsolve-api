package kh.edu.istad.ite.devsoleapi.feature.userprofile.dto;

import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.SocialPlatform;

public record SocialLinkResponse(
        SocialPlatform platform,
        String url
) {
}
