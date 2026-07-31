package kh.edu.istad.ite.devsoleapi.feature.userprofile;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

@Component
public class SocialLinkValidator {

    public String normalize(
            SocialPlatform platform,
            String rawUrl
    ) {
        String url = rawUrl == null ? "" : rawUrl.trim();
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException exception) {
            throw invalidUrl(platform, exception);
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null
                || host == null
                || !(scheme.equalsIgnoreCase("http")
                || scheme.equalsIgnoreCase("https"))) {
            throw invalidUrl(platform, null);
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (!allowedHosts(platform).isEmpty()
                && allowedHosts(platform).stream().noneMatch(
                allowed -> normalizedHost.equals(allowed)
                        || normalizedHost.endsWith("." + allowed)
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    platform.name() + " link must use its official domain"
            );
        }
        return url;
    }

    private Set<String> allowedHosts(SocialPlatform platform) {
        return switch (platform) {
            case GITHUB -> Set.of("github.com");
            case LINKEDIN -> Set.of("linkedin.com");
            case X -> Set.of("x.com", "twitter.com");
            case FACEBOOK -> Set.of("facebook.com");
            case TELEGRAM -> Set.of("t.me", "telegram.me");
            case WEBSITE, OTHER -> Set.of();
        };
    }

    private ResponseStatusException invalidUrl(
            SocialPlatform platform,
            Exception cause
    ) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                platform.name()
                        + " link must be a valid HTTP or HTTPS URL",
                cause
        );
    }
}
