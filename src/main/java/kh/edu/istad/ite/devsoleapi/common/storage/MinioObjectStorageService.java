package kh.edu.istad.ite.devsoleapi.common.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class MinioObjectStorageService implements ObjectStorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

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
            String url = minioClient.getPresignedObjectUrl(
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
