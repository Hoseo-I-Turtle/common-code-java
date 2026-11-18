package com.iturtle.infra.objectstorage;

import com.iturtle.infra.autoconfigure.MinioStorageAutoConfiguration;
import com.iturtle.infra.autoconfigure.S3StorageAutoConfiguration;
import com.iturtle.infra.objectstorage.dto.CompletedPart;
import com.iturtle.infra.objectstorage.dto.DownloadOptions;
import com.iturtle.infra.objectstorage.dto.MultipartUploadSession;
import com.iturtle.infra.objectstorage.dto.StorageUploadCommand;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Garage 에서 type=garage 자동 설정(S3ObjectStorage)을 그대로 검증한다. Docker 가 없으면 건너뛴다.
 * <p>
 * S3Mock 과 달리 Garage 는 서명을 검사하므로 presigned URL 의 서명(위조 거절)까지 확인된다. 최신 AWS SDK 의 기본 요청 체크섬을
 * 받지 못하는 서버라, 엔드포인트를 지정하면 체크섬을 필요할 때만 보내는 설정이 실제로 동작하는지도 함께 확인한다.
 */
@Testcontainers(disabledWithoutDocker = true)
class GarageObjectStorageContainerTest {

    private static final String BUCKET = "test-bucket";
    // garage key import 형식: GK + 24 hex, 비밀 64 hex (테스트 전용 값)
    private static final String ACCESS_KEY = "GK0123456789abcdef01234567";
    private static final String SECRET_KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private static final String CONFIG = """
            metadata_dir = "/var/lib/garage/meta"
            data_dir = "/var/lib/garage/data"
            db_engine = "sqlite"
            replication_factor = 1
            rpc_bind_addr = "[::]:3901"
            rpc_public_addr = "127.0.0.1:3901"
            rpc_secret = "1799bccfd7411eddcf9ebd316bc1f5287ad12a68094e1c6ac6abde7e6feae1ec"

            [s3_api]
            s3_region = "garage"
            api_bind_addr = "[::]:3900"
            """;

    @Container
    static final GenericContainer<?> GARAGE = new GenericContainer<>(DockerImageName.parse("dxflrs/garage:v2.1.0"))
            .withCopyToContainer(Transferable.of(CONFIG), "/etc/garage.toml")
            .withExposedPorts(3900)
            // 이미지에 셸이 없어 포트 검사 대신 로그로 기다린다
            .waitingFor(Wait.forLogMessage(".*S3 API server listening.*", 1).withStartupTimeout(Duration.ofSeconds(60)));

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(S3StorageAutoConfiguration.class, MinioStorageAutoConfiguration.class))
            .withPropertyValues(
                    "iturtle.infra.storage.enabled=true",
                    // type=garage: region · path-style · 체크섬은 자동
                    "iturtle.infra.storage.type=garage",
                    "iturtle.infra.storage.bucket=" + BUCKET,
                    "iturtle.infra.storage.endpoint=http://" + GARAGE.getHost() + ":" + GARAGE.getMappedPort(3900),
                    "iturtle.infra.storage.access-key=" + ACCESS_KEY,
                    "iturtle.infra.storage.secret-key=" + SECRET_KEY);

    @BeforeAll
    static void initGarage() throws Exception {
        String nodeId = retry(() -> garage("node", "id", "-q")).getStdout().strip().split("@")[0];
        garage("layout", "assign", "-z", "dc1", "-c", "1G", nodeId);
        garage("layout", "apply", "--version", "1");
        garage("bucket", "create", BUCKET);
        garage("key", "import", "--yes", "-n", "test", ACCESS_KEY, SECRET_KEY);
        garage("bucket", "allow", "--read", "--write", "--owner", BUCKET, "--key", "test");
    }

    @Test
    void uploadDownloadStatRange() {
        runner.run(context -> {
            ObjectStorage storage = context.getBean(ObjectStorage.class);
            byte[] content = "hello".getBytes(StandardCharsets.UTF_8);

            // 체크섬 설정이 없으면 Garage 가 이 업로드를 거절한다
            storage.upload(new StorageUploadCommand("g/a.txt", new ByteArrayInputStream(content), content.length, "text/plain", "a.txt"));
            try (InputStream in = storage.download("g/a.txt")) {
                assertThat(in.readAllBytes()).isEqualTo(content);
            }
            assertThat(storage.stat("g/a.txt").orElseThrow().sizeBytes()).isEqualTo(content.length);
            assertThat(storage.stat("g/none.txt")).isEmpty();
            try (InputStream in = storage.downloadRange("g/a.txt", 1, 3)) {
                assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("ell");
            }
        });
    }

    @Test
    void presignedUrlsWithSignatureCheckAndDownloadName() {
        runner.run(context -> {
            ObjectStorage storage = context.getBean(ObjectStorage.class);
            HttpClient http = HttpClient.newHttpClient();

            HttpResponse<Void> put = http.send(HttpRequest.newBuilder(storage.createUploadUrl("g/p.txt", Duration.ofMinutes(5)).toURI())
                    .PUT(HttpRequest.BodyPublishers.ofString("via-url")).build(), HttpResponse.BodyHandlers.discarding());
            assertThat(put.statusCode()).isEqualTo(200);

            DownloadOptions options = DownloadOptions.attachment("회의록.txt");
            HttpResponse<String> get = http.send(HttpRequest.newBuilder(
                    storage.createDownloadUrl("g/p.txt", Duration.ofMinutes(5), options).toURI()).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(get.statusCode()).isEqualTo(200);
            assertThat(get.body()).isEqualTo("via-url");
            assertThat(get.headers().firstValue("Content-Disposition")).hasValue(options.contentDispositionHeader());

            // 서명을 바꾸면 거절된다
            String tampered = storage.createDownloadUrl("g/p.txt", Duration.ofMinutes(5)).toString()
                    .replaceAll("X-Amz-Signature=[0-9a-f]{4}", "X-Amz-Signature=0000");
            HttpResponse<String> rejected = http.send(HttpRequest.newBuilder(URI.create(tampered)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(rejected.statusCode()).isEqualTo(403);
        });
    }

    @Test
    void multipartUploadWithPresignedParts() {
        runner.run(context -> {
            ObjectStorage storage = context.getBean(ObjectStorage.class);
            HttpClient http = HttpClient.newHttpClient();
            byte[] first = new byte[5 * 1024 * 1024];
            Arrays.fill(first, (byte) 'a');
            byte[] second = "tail".getBytes(StandardCharsets.UTF_8);

            MultipartUploadSession session = storage.initiateMultipartUpload("g/multi.bin", "application/octet-stream");
            String etag1 = putPart(http, storage.createUploadPartUrl("g/multi.bin", session.uploadId(), 1, Duration.ofMinutes(5)).toURI(), first);
            String etag2 = putPart(http, storage.createUploadPartUrl("g/multi.bin", session.uploadId(), 2, Duration.ofMinutes(5)).toURI(), second);
            storage.completeMultipartUpload("g/multi.bin", session.uploadId(),
                    List.of(new CompletedPart(2, etag2), new CompletedPart(1, etag1)));
            assertThat(storage.stat("g/multi.bin").orElseThrow().sizeBytes()).isEqualTo(first.length + second.length);

            MultipartUploadSession aborted = storage.initiateMultipartUpload("g/aborted.bin", null);
            storage.abortMultipartUpload("g/aborted.bin", aborted.uploadId());
            storage.abortMultipartUpload("g/aborted.bin", aborted.uploadId());
            assertThat(storage.exists("g/aborted.bin")).isFalse();
        });
    }

    private static String putPart(HttpClient http, URI uri, byte[] body) throws Exception {
        HttpResponse<Void> response = http.send(HttpRequest.newBuilder(uri).PUT(HttpRequest.BodyPublishers.ofByteArray(body)).build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.headers().firstValue("ETag").orElseThrow();
    }

    private interface Call {
        ExecResult run() throws Exception;
    }

    /**
     * 서버가 막 떠서 RPC 가 아직 준비되지 않았으면 잠깐 뒤 다시 시도한다.
     */
    private static ExecResult retry(Call call) throws Exception {
        IllegalStateException last = null;
        for (int i = 0; i < 20; i++) {
            try {
                return call.run();
            } catch (IllegalStateException e) {
                last = e;
                Thread.sleep(500);
            }
        }
        throw last;
    }

    private static ExecResult garage(String... args) throws Exception {
        String[] command = new String[args.length + 3];
        command[0] = "/garage";
        command[1] = "-c";
        command[2] = "/etc/garage.toml";
        System.arraycopy(args, 0, command, 3, args.length);
        ExecResult result = GARAGE.execInContainer(command);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException(String.join(" ", args) + " 실패: " + result.getStderr());
        }
        return result;
    }
}
