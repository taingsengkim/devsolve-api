package kh.edu.istad.ite.devsoleapi.common.storage;

import kh.edu.istad.ite.devsoleapi.common.attachment.AttachmentValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.UUID;

/**
 * Handles the "one image, replaced not appended" case shared by category
 * icons, organization logos, avatars and banners.
 *
 * <p>Attachments are deliberately not routed through here: they are many per
 * parent, privately stored, and carry their own rows. See
 * {@code ProblemServiceImpl#uploadAttachment}.
 *
 * <p>Callers own the permission check on the parent entity before calling.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImageStorageService {

    private final AttachmentValidator attachmentValidator;
    private final ObjectStorageService objectStorageService;

    /**
     * Validates and stores {@code file}, returning the public URL to persist
     * on the parent entity.
     *
     * <p>The previous object is removed only once the caller's transaction
     * commits, and the newly stored object is removed if it rolls back, so
     * neither outcome leaves the bucket disagreeing with the database.
     *
     * @param pathPrefix     key namespace, e.g. {@code "categories/<id>"}
     * @param currentImageUrl image being replaced, or {@code null}
     */
    public String replace(
            String pathPrefix,
            String currentImageUrl,
            MultipartFile file
    ) {
        AttachmentValidator.ValidatedAttachment validated =
                attachmentValidator.validate(
                        file,
                        AttachmentValidator.Profile.IMAGE
                );

        String storageKey = ObjectStorageService.PUBLIC_PREFIX
                + trimSlashes(pathPrefix)
                + "/"
                + UUID.randomUUID()
                + "."
                + validated.extension();

        objectStorageService.store(
                storageKey,
                new ByteArrayInputStream(validated.content()),
                validated.sizeBytes(),
                validated.mimeType()
        );

        scheduleCleanup(storageKey, currentImageUrl);
        return objectStorageService.publicUrl(storageKey);
    }

    /**
     * Drops the object behind {@code currentImageUrl} once the caller's
     * transaction commits. A no-op for externally hosted URLs.
     */
    public void remove(String currentImageUrl) {
        scheduleCleanup(null, currentImageUrl);
    }

    /**
     * Narrows {@code candidateImageUrl} to the image that is safe to pass to
     * {@link #replace} or {@link #remove} as the outgoing one: an image a
     * still-live record ({@code retainedImageUrl}) also points at is shared,
     * not superseded, so it is dropped from consideration.
     *
     * <p>A draft record — a showcase revision awaiting moderation — starts out
     * pointing at the published record's image. Deleting it there would blank
     * the live page while the draft is still being reviewed.
     */
    public static String supersededUrl(
            String candidateImageUrl,
            String retainedImageUrl
    ) {
        return candidateImageUrl != null
                && candidateImageUrl.equals(retainedImageUrl)
                ? null
                : candidateImageUrl;
    }

    private void scheduleCleanup(
            String newStorageKey,
            String previousImageUrl
    ) {
        String previousStorageKey = objectStorageService
                .storageKeyOf(previousImageUrl)
                .orElse(null);

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(previousStorageKey);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        deleteQuietly(
                                status == STATUS_COMMITTED
                                        ? previousStorageKey
                                        : newStorageKey
                        );
                    }
                }
        );
    }

    private void deleteQuietly(String storageKey) {
        if (storageKey == null) {
            return;
        }
        try {
            objectStorageService.delete(storageKey);
        } catch (RuntimeException exception) {
            log.warn(
                    "Unable to delete stored image {}",
                    storageKey,
                    exception
            );
        }
    }

    private String trimSlashes(String value) {
        return value.replaceAll("^/+", "").replaceAll("/+$", "");
    }
}
