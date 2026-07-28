package kh.edu.istad.ite.devsoleapi.common.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

public interface ObjectStorageService {

    void store(
            String storageKey,
            InputStream content,
            long sizeBytes,
            String contentType
    );

    void delete(String storageKey);

    URI createDownloadUrl(String storageKey, Duration validity);
}
