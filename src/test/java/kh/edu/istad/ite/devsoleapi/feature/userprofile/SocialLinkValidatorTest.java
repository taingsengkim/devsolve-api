package kh.edu.istad.ite.devsoleapi.feature.userprofile;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SocialLinkValidatorTest {

    private final SocialLinkValidator validator =
            new SocialLinkValidator();

    @Test
    void acceptsOfficialPlatformAndCustomWebsiteLinks() {
        assertEquals(
                "https://github.com/sokha-chan",
                validator.normalize(
                        SocialPlatform.GITHUB,
                        "  https://github.com/sokha-chan  "
                )
        );
        assertEquals(
                "https://portfolio.example.com",
                validator.normalize(
                        SocialPlatform.WEBSITE,
                        "https://portfolio.example.com"
                )
        );
    }

    @Test
    void rejectsWrongPlatformDomain() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> validator.normalize(
                        SocialPlatform.GITHUB,
                        "https://malicious.example.com/fake-github"
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void rejectsUnsafeUrlScheme() {
        assertThrows(
                ResponseStatusException.class,
                () -> validator.normalize(
                        SocialPlatform.WEBSITE,
                        "javascript:alert(1)"
                )
        );
    }
}
