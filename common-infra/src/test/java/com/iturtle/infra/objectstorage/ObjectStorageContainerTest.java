package com.iturtle.infra.objectstorage;

import com.iturtle.infra.autoconfigure.MinioStorageAutoConfiguration;
import com.iturtle.infra.autoconfigure.S3StorageAutoConfiguration;
import com.iturtle.infra.objectstorage.dto.StorageUploadCommand;
import com.iturtle.infra.objectstorage.dto.StorageUploadResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S3 호환 서버(S3Mock)에서 S3ObjectStorage / MinioObjectStorage 를 자동 설정 그대로 검증한다. Docker 가 없으면 건너뛴다.
 * <p>
 * S3Mock 은 서명을 검사하지 않으므로 presigned URL 은 주소와 동작(PUT / GET)만 확인된다. 서명 자체는 검증되지 않는다.
 */
@Testcontainers(disabledWithoutDocker = true)
class ObjectStorageContainerTest {

    private static final String BUCKET = "test-bucket";

    @Container
    static final GenericContainer<?> S3 = new GenericContainer<>(DockerImageName.parse("adobe/s3mock:5.2.3"))
            .withExposedPorts(9090);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(S3StorageAutoConfiguration.class, MinioStorageAutoConfiguration.class));

    @BeforeAll
    static void createBucket() {
        try (S3Client client = S3Client.builder()
                .endpointOverride(URI.create(endpoint()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("ak", "sk")))
                .forcePathStyle(true)
                .build()) {
            client.createBucket(r -> r.bucket(BUCKET));
        }
    }

    @Test
    void s3() {
        runner.withPropertyValues(properties("s3"))
                .withPropertyValues("iturtle.infra.storage.path-style-access=true")
                .run(context -> scenario(context.getBean(ObjectStorage.class), "s3"));
    }

    @Test
    void minio() {
        runner.withPropertyValues(properties("minio"))
                .run(context -> scenario(context.getBean(ObjectStorage.class), "minio"));
    }

    private void scenario(ObjectStorage storage, String prefix) throws Exception {
        String key = prefix + "/a.txt";
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);

        StorageUploadResult result = storage.upload(new StorageUploadCommand(
                key, new ByteArrayInputStream(content), content.length, "text/plain", "a.txt"));
        assertThat(result).isEqualTo(new StorageUploadResult(key, "a.txt", "text/plain", content.length));
        assertThat(storage.exists(key)).isTrue();
        try (InputStream in = storage.download(key)) {
            assertThat(in.readAllBytes()).isEqualTo(content);
        }

        // presigned URL 로 서버를 거치지 않고 올리고 받는다
        HttpClient http = HttpClient.newHttpClient();
        String presignedKey = prefix + "/presigned.txt";
        HttpResponse<Void> put = http.send(
                HttpRequest.newBuilder(storage.createUploadUrl(presignedKey, Duration.ofMinutes(5)).toURI())
                        .PUT(HttpRequest.BodyPublishers.ofString("via-url")).build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(put.statusCode()).isEqualTo(200);
        HttpResponse<String> get = http.send(
                HttpRequest.newBuilder(storage.createDownloadUrl(presignedKey, Duration.ofMinutes(5)).toURI()).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(get.statusCode()).isEqualTo(200);
        assertThat(get.body()).isEqualTo("via-url");

        storage.delete(key);
        assertThat(storage.exists(key)).isFalse();
        storage.delete(key);   // 없는 키를 지워도 실패하지 않는다

        String missing = prefix + "/missing.txt";
        assertThat(storage.exists(missing)).isFalse();
        assertThatThrownBy(() -> storage.download(missing)).isInstanceOf(StorageException.class);
    }

    private static String[] properties(String type) {
        return new String[]{
                "iturtle.infra.storage.enabled=true",
                "iturtle.infra.storage.type=" + type,
                "iturtle.infra.storage.bucket=" + BUCKET,
                "iturtle.infra.storage.region=us-east-1",
                "iturtle.infra.storage.endpoint=" + endpoint(),
                "iturtle.infra.storage.access-key=ak",
                "iturtle.infra.storage.secret-key=sk"};
    }

    private static String endpoint() {
        return "http://" + S3.getHost() + ":" + S3.getMappedPort(9090);
    }
}
