package kh.edu.istad.ite.devsoleapi.feature.userprofile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.SocialPlatform;

public record SocialLinkRequest(
        @NotNull(message = "Social platform is required")
        SocialPlatform platform,

        @NotBlank(message = "Social link URL is required")
        @Size(max = 2048, message = "Social link URL is too long")
        String url
) {
}
