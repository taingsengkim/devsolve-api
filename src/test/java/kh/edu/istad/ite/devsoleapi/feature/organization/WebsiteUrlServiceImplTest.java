package kh.edu.istad.ite.devsoleapi.feature.organization;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebsiteUrlServiceImplTest {

    private final WebsiteUrlService websiteUrlService = new WebsiteUrlServiceImpl();

    @Test
    void normalizesWebsiteAndRemovesFragment() {
        assertEquals(
                "https://www.acme.com/about",
                websiteUrlService.normalize("www.acme.com/about#team")
        );
    }

    @Test
    void extractsCanonicalDomain() {
        assertEquals(
                "acme.com",
                websiteUrlService.extractDomain("https://www.acme.com")
        );
    }

    @Test
    void matchesBusinessEmailToWebsiteDomain() {
        assertTrue(
                websiteUrlService.matchesEmailDomain(
                        "security@acme.com",
                        "https://www.acme.com"
                )
        );
        assertFalse(
                websiteUrlService.matchesEmailDomain(
                        "security@gmail.com",
                        "https://www.acme.com"
                )
        );
    }

    @Test
    void rejectsNonHttpWebsiteUrl() {
        assertThrows(
                ResponseStatusException.class,
                () -> websiteUrlService.normalize("ftp://acme.com")
        );
    }
}
