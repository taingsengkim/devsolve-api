package kh.edu.istad.ite.devsoleapi.common.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;

public interface ObjectStorageService {

    /**
     * Key prefix whose objects are readable without credentials. Anything
     * stored outside it stays private and must be served through
     * {@link #createDownloadUrl(String, Duration)}.
     */
    String PUBLIC_PREFIX = "public/";

    void store(
            String storageKey,
            InputStream content,
            long sizeBytes,
            String contentType
    );

    void delete(String storageKey);

    URI createDownloadUrl(String storageKey, Duration validity);

    /**
     * Stable, non-expiring URL for an object under {@link #PUBLIC_PREFIX}.
     * Suitable for an {@code <img src>} that gets cached by browsers.
     */
    String publicUrl(String storageKey);

    /**
     * Reverses {@link #publicUrl(String)}. Returns empty for URLs this
     * service did not produce, so externally hosted images are never
     * mistaken for our own objects and deleted.
     */
    Optional<String> storageKeyOf(String publicUrl);
}
