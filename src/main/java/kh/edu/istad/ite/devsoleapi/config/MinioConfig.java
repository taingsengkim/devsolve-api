package kh.edu.istad.ite.devsoleapi.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class MinioConfig {

    /**
     * The client the application talks to MinIO with, over whatever address
     * reaches it from inside the network.
     */
    @Bean
    @Primary
    MinioClient minioClient(
            @Value("${minio.url}") String url,
            @Value("${minio.access-key}") String accessKey,
            @Value("${minio.secret-key}") String secretKey
    ) {
        return MinioClient.builder()
                .endpoint(url)
                .credentials(accessKey, secretKey)
                .build();
    }

    /**
     * A second client that never makes a request: it exists to sign download
     * URLs against the address a browser uses.
     *
     * <p>A presigned URL is signed over its own host, so one produced by the
     * client above names the internal address and is only valid for it. Where
     * those two addresses differ — the app reaching MinIO over the compose
     * network while clients come in through a public hostname — every
     * attachment link pointed somewhere the browser could not go, and the
     * image simply failed to load. Public avatars and logos were unaffected,
     * because those are plain URLs built from {@code minio.public-url} rather
     * than signed ones, which is what made this look like a problem with
     * attachments specifically.
     *
     * <p>Rewriting the host afterwards is not an option: it is part of what
     * was signed, so the object store rejects the result. The URL has to be
     * generated against the public address in the first place, and signing is
     * local arithmetic, so a client that only ever does that costs a builder
     * call and no connection.
     *
     * <p>Falls back to {@code minio.url} exactly as
     * {@code MinioObjectStorageService} does for public URLs, so a deployment
     * with one address for everybody keeps working unchanged.
     */
    @Bean
    MinioClient minioPresignClient(
            @Value("${minio.url}") String url,
            @Value("${minio.public-url:}") String publicUrl,
            @Value("${minio.access-key}") String accessKey,
            @Value("${minio.secret-key}") String secretKey
    ) {
        String endpoint = publicUrl == null || publicUrl.isBlank()
                ? url
                : publicUrl.trim();

        return MinioClient.builder()
                .endpoint(trimTrailingSlash(endpoint))
                .credentials(accessKey, secretKey)
                .build();
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/")
                ? value.substring(0, value.length() - 1)
                : value;
    }
}
