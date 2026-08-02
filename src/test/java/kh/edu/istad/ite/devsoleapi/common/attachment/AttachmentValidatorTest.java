package kh.edu.istad.ite.devsoleapi.common.attachment;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AttachmentValidatorTest {

    private final AttachmentValidator validator =
            new AttachmentValidator();

    @Test
    void acceptsSanitizedUtf8Text() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "logs/debug.log",
                "text/plain",
                "Safe diagnostic output".getBytes(StandardCharsets.UTF_8)
        );

        AttachmentValidator.ValidatedAttachment result =
                validator.validate(file);

        assertEquals("debug.log", result.originalFileName());
        assertEquals("log", result.extension());
        assertEquals("text/plain", result.mimeType());
    }

    @Test
    void acceptsPngImageWithMatchingSignature() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "proof.png",
                "image/png",
                new byte[]{
                        (byte) 0x89, 0x50, 0x4E, 0x47,
                        0x0D, 0x0A, 0x1A, 0x0A
                }
        );

        AttachmentValidator.ValidatedAttachment result =
                validator.validate(file);

        assertEquals("proof.png", result.originalFileName());
        assertEquals("png", result.extension());
        assertEquals("image/png", result.mimeType());
    }

    @Test
    void rejectsFileLargerThanTenMib() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.txt",
                "text/plain",
                new byte[(int) AttachmentValidator.MAX_FILE_SIZE + 1]
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
