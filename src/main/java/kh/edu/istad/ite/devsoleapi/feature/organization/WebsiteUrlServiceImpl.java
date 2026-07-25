package kh.edu.istad.ite.devsoleapi.feature.organization;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

@Service
public class WebsiteUrlServiceImpl implements WebsiteUrlService {

    @Override
    public String normalize(String websiteUrl) {
        URI uri = parse(websiteUrl);

        try {
            return new URI(
                    uri.getScheme().toLowerCase(Locale.ROOT),
                    null,
                    uri.getHost().toLowerCase(Locale.ROOT),
                    uri.getPort(),
                    normalizePath(uri.getPath()),
                    uri.getQuery(),
                    null
            ).toString();
        } catch (URISyntaxException e) {
            throw invalidWebsiteUrl(e);
        }
    }

    @Override
    public String extractDomain(String websiteUrl) {
        String domain = IDN.toASCII(parse(websiteUrl).getHost())
                .toLowerCase(Locale.ROOT);
        return domain.startsWith("www.") ? domain.substring(4) : domain;
    }

    @Override
    public boolean matchesEmailDomain(String email, String websiteUrl) {
        if (email == null) {
            return false;
        }

        int separatorIndex = email.lastIndexOf('@');
        if (separatorIndex < 1 || separatorIndex == email.length() - 1) {
            return false;
        }

        String emailDomain = IDN.toASCII(email.substring(separatorIndex + 1))
                .toLowerCase(Locale.ROOT);
        return emailDomain.equals(extractDomain(websiteUrl));
    }

    private URI parse(String websiteUrl) {
        if (websiteUrl == null || websiteUrl.isBlank()) {
            throw invalidWebsiteUrl(null);
        }

        String normalized = websiteUrl.trim();
        if (normalized.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")
                && !normalized.matches("(?i)^https?://.*")) {
            throw invalidWebsiteUrl(null);
        }
        if (!normalized.matches("(?i)^https?://.*")) {
            normalized = "https://" + normalized;
        }

        try {
            URI uri = new URI(normalized).normalize();
            String scheme = uri.getScheme();
            if (scheme == null
                    || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                throw invalidWebsiteUrl(null);
            }
            return uri;
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw invalidWebsiteUrl(exception);
        }
    }

    private String normalizePath(String path) {
        return path == null || path.isBlank() ? null : path;
    }

    private ResponseStatusException invalidWebsiteUrl(Throwable cause) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Website URL must be a valid HTTP or HTTPS URL",
                cause
        );
    }
}
