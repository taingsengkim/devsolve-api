package kh.edu.istad.ite.devsoleapi.feature.problem;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProblemAttachmentValidatorTest {

    private final ProblemAttachmentValidator validator =
            new ProblemAttachmentValidator();

    @Test
    void acceptsSanitizedUtf8Text() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "logs/debug.log",
                "text/plain",
                "Safe diagnostic output".getBytes(StandardCharsets.UTF_8)
        );

        ProblemAttachmentValidator.ValidatedAttachment result =
                validator.validate(file);

        assertEquals("debug.log", result.originalFileName());
        assertEquals("log", result.extension());
        assertEquals("text/plain", result.mimeType());
    }

    @Test
    void rejectsFileLargerThanTenMib() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.txt",
                "text/plain",
                new byte[(int) ProblemAttachmentValidator.MAX_FILE_SIZE + 1]
        );

        assertThrows(
                ResponseStatusException.class,
                () -> validator.validate(file)
        );
    }

    @Test
    void rejectsMismatchedExtensionAndMimeType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "proof.png",
                "application/pdf",
                "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)
        );

        assertThrows(
                ResponseStatusException.class,
                () -> validator.validate(file)
        );
    }

    @Test
    void rejectsExecutableContentDisguisedAsText() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "payload.txt",
                "text/plain",
                new byte[]{'M', 'Z', 0, 0}
        );

        assertThrows(
                ResponseStatusException.class,
                () -> validator.validate(file)
        );
    }
}
