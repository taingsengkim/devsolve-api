package kh.edu.istad.ite.devsoleapi.feature.problem;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.InvalidPathException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class ProblemAttachmentValidator {

    public static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    private static final Map<String, Set<String>> MIME_TYPES = Map.of(
            "pdf", Set.of("application/pdf"),
            "doc", Set.of("application/msword"),
            "docx", Set.of(
                    "application/vnd.openxmlformats-officedocument"
                            + ".wordprocessingml.document"
            ),
            "png", Set.of("image/png"),
            "jpg", Set.of("image/jpeg"),
            "jpeg", Set.of("image/jpeg"),
            "webp", Set.of("image/webp"),
            "txt", Set.of("text/plain"),
            "log", Set.of("text/plain")
    );

    public ValidatedAttachment validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("Attachment cannot be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw badRequest("Attachment cannot exceed 10 MiB");
        }

        String originalFileName = sanitizeFileName(
                file.getOriginalFilename()
        );
        String extension = extensionOf(originalFileName);
        Set<String> allowedMimeTypes = MIME_TYPES.get(extension);
        if (allowedMimeTypes == null) {
            throw badRequest(
                    "Allowed attachment types are PDF, DOC, DOCX, PNG, "
                            + "JPG, JPEG, WEBP, TXT, and LOG"
            );
        }

        String mimeType = normalizeMimeType(file.getContentType());
        if (!allowedMimeTypes.contains(mimeType)) {
            throw badRequest(
                    "The attachment extension does not match its MIME type"
            );
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw badRequest("Unable to read the attachment");
        }

        rejectExecutableHeader(content);
        if (!matchesFileSignature(extension, content)) {
            throw badRequest(
                    "The attachment content does not match its file type"
            );
        }

        return new ValidatedAttachment(
                originalFileName,
                extension,
                mimeType,
                content
        );
    }

    private boolean matchesFileSignature(
            String extension,
            byte[] content
    ) {
        return switch (extension) {
            case "pdf" -> startsWith(content, "%PDF-".getBytes(
                    StandardCharsets.US_ASCII
            ));
            case "png" -> startsWith(
                    content,
                    new byte[]{
                            (byte) 0x89, 0x50, 0x4E, 0x47,
                            0x0D, 0x0A, 0x1A, 0x0A
                    }
            );
            case "jpg", "jpeg" -> content.length >= 3
                    && (content[0] & 0xFF) == 0xFF
                    && (content[1] & 0xFF) == 0xD8
                    && (content[2] & 0xFF) == 0xFF;
            case "webp" -> content.length >= 12
                    && asciiEquals(content, 0, "RIFF")
                    && asciiEquals(content, 8, "WEBP");
            case "doc" -> startsWith(
                    content,
                    new byte[]{
                            (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                            (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
                    }
            );
            case "docx" -> isWordDocumentArchive(content);
            case "txt", "log" -> isSafeText(content);
            default -> false;
        };
    }

    private boolean isWordDocumentArchive(byte[] content) {
        boolean hasContentTypes = false;
        boolean hasWordDocument = false;
        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(content)
        )) {
            ZipEntry entry;
            int inspected = 0;
            while ((entry = zip.getNextEntry()) != null && inspected++ < 500) {
                String name = entry.getName();
                hasContentTypes |= "[Content_Types].xml".equals(name);
                hasWordDocument |= "word/document.xml".equals(name);
                if (hasContentTypes && hasWordDocument) {
                    return true;
                }
            }
            return false;
        } catch (IOException exception) {
            return false;
        }
    }

    private boolean isSafeText(byte[] content) {
        if (content.length >= 2
                && content[0] == '#'
                && content[1] == '!') {
            return false;
        }
        for (byte value : content) {
            int unsigned = value & 0xFF;
            if (unsigned == 0) {
                return false;
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
            return true;
        } catch (CharacterCodingException exception) {
            return false;
        }
    }

    private void rejectExecutableHeader(byte[] content) {
        boolean windowsExecutable = content.length >= 2
                && content[0] == 'M'
                && content[1] == 'Z';
        boolean elfExecutable = content.length >= 4
                && (content[0] & 0xFF) == 0x7F
                && content[1] == 'E'
                && content[2] == 'L'
                && content[3] == 'F';
        if (windowsExecutable || elfExecutable) {
            throw badRequest("Executable attachments are not allowed");
        }
    }

    private String sanitizeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw badRequest("Attachment filename is required");
        }
        String normalized = originalFileName.replace('\\', '/');
        String fileName;
        try {
            fileName = Path.of(normalized).getFileName().toString().trim();
        } catch (InvalidPathException exception) {
            throw badRequest("Attachment filename is invalid");
        }
        if (fileName.isBlank() || fileName.length() > 255) {
            throw badRequest("Attachment filename is invalid");
        }
        return fileName;
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            throw badRequest("Attachment must have a supported extension");
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeMimeType(String contentType) {
        if (contentType == null) {
            return "";
        }
        return contentType.split(";", 2)[0]
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (content[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean asciiEquals(
            byte[] content,
            int offset,
            String expected
    ) {
        if (content.length < offset + expected.length()) {
            return false;
        }
        for (int index = 0; index < expected.length(); index++) {
            if (content[offset + index] != (byte) expected.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record ValidatedAttachment(
            String originalFileName,
            String extension,
            String mimeType,
            byte[] content
    ) {
        public long sizeBytes() {
            return content.length;
        }
    }
}
