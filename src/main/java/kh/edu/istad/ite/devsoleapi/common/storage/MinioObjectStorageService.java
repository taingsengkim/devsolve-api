package kh.edu.istad.ite.devsoleapi.common.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetBucketPolicyArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class MinioObjectStorageService implements ObjectStorageService {

    private final MinioClient minioClient;

    /**
     * Signs download URLs against the address browsers use. Separate from
     * {@link #minioClient} because a presigned URL is only valid for the host
     * it was signed against — see {@code MinioConfig#minioPresignClient}.
     */
    private final MinioClient presignClient;

    public MinioObjectStorageService(
            MinioClient minioClient,
            @Qualifier("minioPresignClient") MinioClient presignClient
    ) {
        this.minioClient = minioClient;
        this.presignClient = presignClient;
    }

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${minio.url}")
    private String internalBaseUrl;

    /**
     * Browser-reachable base URL. Differs from {@code minio.url} whenever the
     * app talks to MinIO over an internal address but clients do not. Blank
     * when unset, which {@link #resolvePublicBaseUrl()} folds back to
     * {@link #internalBaseUrl}.
     */
    @Value("${minio.public-url:}")
    private String configuredPublicBaseUrl;

    /** {@link #configuredPublicBaseUrl} resolved and normalised once. */
    private String publicBaseUrl;

    @PostConstruct
    void resolvePublicBaseUrl() {
        String resolved =
                configuredPublicBaseUrl == null
                        || configuredPublicBaseUrl.isBlank()
                        ? internalBaseUrl
                        : configuredPublicBaseUrl;
        publicBaseUrl = trimTrailingSlash(resolved.trim());
        log.info(
                "MinIO public base URL resolved to {}",
                publicBaseUrl
        );
    }

    /**
     * The public-prefix policy only has to be reconciled once per process;
     * without this every upload would pay an extra round trip to MinIO.
     */
    private final AtomicBoolean publicPolicyReconciled = new AtomicBoolean();

    @Override
    public void store(
            String storageKey,
            InputStream content,
            long sizeBytes,
            String contentType
    ) {
        try {
            ensureBucketExists();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(storageKey)
                            .stream(content, sizeBytes, -1L)
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception exception) {
            throw storageFailure("Unable to store the attachment", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(storageKey)
                            .build()
            );
        } catch (Exception exception) {
            throw storageFailure("Unable to delete the attachment", exception);
        }
    }

    @Override
    public URI createDownloadUrl(
            String storageKey,
            Duration validity
    ) {
        try {
            String url = presignClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Http.Method.GET)
                            .bucket(bucket)
                            .object(storageKey)
                            .expiry(
                                    Math.toIntExact(validity.toSeconds()),
                                    TimeUnit.SECONDS
                            )
                            .build()
            );
            return URI.create(url);
        } catch (Exception exception) {
            throw storageFailure(
                    "Unable to create an attachment download link",
                    exception
            );
        }
    }

    @Override
    public String publicUrl(String storageKey) {
        return publicUrlPrefix() + storageKey;
    }

    @Override
    public Optional<String> storageKeyOf(String publicUrl) {
        if (publicUrl == null || publicUrl.isBlank()) {
            return Optional.empty();
        }
        String prefix = publicUrlPrefix();
        if (!publicUrl.startsWith(prefix)) {
            return Optional.empty();
        }
        String storageKey = publicUrl.substring(prefix.length());
        if (storageKey.isBlank() || !storageKey.startsWith(PUBLIC_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(storageKey);
    }

    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder()
                        .bucket(bucket)
                        .build()
        );
        if (!exists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder()
                            .bucket(bucket)
                            .build()
            );
        }
        ensurePublicPrefixIsReadable();
    }

    /**
     * Grants anonymous reads under {@link #PUBLIC_PREFIX} so uploaded icons
     * and logos work in an {@code <img src>}. An operator-managed policy is
     * left untouched rather than clobbered.
     */
    private void ensurePublicPrefixIsReadable() {
        if (!publicPolicyReconciled.compareAndSet(false, true)) {
            return;
        }
        try {
            String existing = minioClient.getBucketPolicy(
                    GetBucketPolicyArgs.builder()
                            .bucket(bucket)
                            .build()
            );
            if (existing != null && !existing.isBlank()) {
                return;
            }
        } catch (Exception exception) {
            log.debug(
                    "Could not read the policy for bucket {}; "
                            + "attempting to apply the public-read policy",
                    bucket,
                    exception
            );
        }

        String policy = """
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {
                      "Effect": "Allow",
                      "Principal": {"AWS": ["*"]},
                      "Action": ["s3:GetObject"],
                      "Resource": ["arn:aws:s3:::%s/%s*"]
                    }
                  ]
                }
                """.formatted(bucket, PUBLIC_PREFIX);

        try {
            minioClient.setBucketPolicy(
                    SetBucketPolicyArgs.builder()
                            .bucket(bucket)
                            .config(policy)
                            .build()
            );
        } catch (Exception exception) {
            log.warn(
                    "Unable to apply the public-read policy to {}/{}*; "
                            + "public images will not be anonymously readable",
                    bucket,
                    PUBLIC_PREFIX,
                    exception
            );
        }
    }

    private String publicUrlPrefix() {
        return publicBaseUrl + "/" + bucket + "/";
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/")
                ? value.substring(0, value.length() - 1)
                : value;
    }

    private ResponseStatusException storageFailure(
            String message,
            Exception cause
    ) {
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                message,
                cause
        );
    }
}
