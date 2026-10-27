package com.iturtle.infra.objectstorage;

import com.iturtle.core.exception.InvalidConfigurationException;
import com.iturtle.infra.autoconfigure.MinioStorageAutoConfiguration;
import com.iturtle.infra.autoconfigure.S3StorageAutoConfiguration;
import com.iturtle.infra.objectstorage.minio.MinioObjectStorage;
import com.iturtle.infra.objectstorage.s3.S3ObjectStorage;
import io.awspring.cloud.autoconfigure.s3.S3AutoConfiguration;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URL;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 스토리지 자동 설정의 등록 조건과 presigned URL 생성을 검증한다. presigned URL 은 서명만 하므로 서버 없이 확인할 수 있다.
 */
class StorageAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(S3StorageAutoConfiguration.class, MinioStorageAutoConfiguration.class));

    @Test
    void notRegisteredByDefault() {
        runner.withPropertyValues("iturtle.infra.storage.type=s3", "iturtle.infra.storage.bucket=b")
                .run(context -> assertThat(context).doesNotHaveBean(ObjectStorage.class));
    }

    @Test
    void s3() {
        runner.withPropertyValues(
                        "iturtle.infra.storage.enabled=true",
                        "iturtle.infra.storage.type=s3",
                        "iturtle.infra.storage.bucket=my-bucket",
                        "iturtle.infra.storage.region=ap-northeast-2",
                        "iturtle.infra.storage.access-key=ak",
                        "iturtle.infra.storage.secret-key=sk")
                .run(context -> {
                    assertThat(context).getBean(ObjectStorage.class).isInstanceOf(S3ObjectStorage.class);
                    assertThat(context).doesNotHaveBean(MinioClient.class);

                    URL url = context.getBean(ObjectStorage.class)
                            .createDownloadUrl("posts/a.png", Duration.ofMinutes(5));
                    assertThat(url.toString()).contains("my-bucket", "posts/a.png", "X-Amz-Expires=300");
                });
    }

    @Test
    void minio() {
        runner.withPropertyValues(
                        "iturtle.infra.storage.enabled=true",
                        "iturtle.infra.storage.type=minio",
                        "iturtle.infra.storage.bucket=my-bucket",
                        "iturtle.infra.storage.region=us-east-1",
                        "iturtle.infra.storage.endpoint=http://localhost:9000",
                        "iturtle.infra.storage.access-key=ak",
                        "iturtle.infra.storage.secret-key=sk")
                .run(context -> {
                    assertThat(context).getBean(ObjectStorage.class).isInstanceOf(MinioObjectStorage.class);
                    assertThat(context).doesNotHaveBean(S3Client.class);

                    URL url = context.getBean(ObjectStorage.class)
                            .createUploadUrl("posts/a.png", Duration.ofMinutes(5));
                    assertThat(url.toString()).startsWith("http://localhost:9000/my-bucket/posts/a.png")
                            .contains("X-Amz-Expires=300");
                });
    }

    @Test
    void notRegisteredWithoutSdk() {
        runner.withClassLoader(new FilteredClassLoader(S3Client.class))
                .withPropertyValues(
                        "iturtle.infra.storage.enabled=true",
                        "iturtle.infra.storage.type=s3",
                        "iturtle.infra.storage.bucket=my-bucket")
                .run(context -> assertThat(context).doesNotHaveBean(ObjectStorage.class));
    }

    @Test
    void failsWithoutRequiredProperty() {
        runner.withPropertyValues(
                        "iturtle.infra.storage.enabled=true",
                        "iturtle.infra.storage.type=minio",
                        "iturtle.infra.storage.bucket=my-bucket",
                        "iturtle.infra.storage.access-key=ak",
                        "iturtle.infra.storage.secret-key=sk")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().isInstanceOf(InvalidConfigurationException.class)
                        .hasMessageContaining("iturtle.infra.storage.endpoint"));
    }

    @Test
    void springCloudAwsClientUsed() {
        // S3AutoConfiguration(Spring Cloud AWS)보다 늦게 실행되어 그쪽 S3Client 를 쓴다. 등록 순서와 무관하다
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(S3StorageAutoConfiguration.class, S3AutoConfiguration.class))
                .withPropertyValues(
                        "iturtle.infra.storage.enabled=true",
                        "iturtle.infra.storage.type=s3",
                        "iturtle.infra.storage.bucket=my-bucket")
                .run(context -> {
                    assertThat(context).getBean(ObjectStorage.class).isInstanceOf(S3ObjectStorage.class);
                    assertThat(context).getBean(S3Client.class).isSameAs(S3AutoConfiguration.CLIENT);
                    assertThat(context).getBean(S3Presigner.class).isSameAs(S3AutoConfiguration.PRESIGNER);
                });
    }

    @Test
    void springCloudAwsWithClientPropertiesFails() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(S3StorageAutoConfiguration.class, S3AutoConfiguration.class))
                .withPropertyValues(
                        "iturtle.infra.storage.enabled=true",
                        "iturtle.infra.storage.type=s3",
                        "iturtle.infra.storage.bucket=my-bucket",
                        "iturtle.infra.storage.region=ap-northeast-2")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().isInstanceOf(InvalidConfigurationException.class)
                        .hasMessageContaining("S3Client 빈이 이미 있으므로"));
    }

    @Test
    void serviceS3ClientWithoutPresignerFails() {
        runner.withBean(S3Client.class, () -> mock(S3Client.class))
                .withPropertyValues(
                        "iturtle.infra.storage.enabled=true",
                        "iturtle.infra.storage.type=s3",
                        "iturtle.infra.storage.bucket=my-bucket")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().isInstanceOf(InvalidConfigurationException.class)
                        .hasMessageContaining("S3Presigner"));
    }

    @Test
    void serviceMinioClientUsed() {
        MinioClient client = MinioClient.builder().endpoint("http://localhost:9000").credentials("ak", "sk").build();
        runner.withBean(MinioClient.class, () -> client)
                .withPropertyValues(
                        "iturtle.infra.storage.enabled=true",
                        "iturtle.infra.storage.type=minio",
                        "iturtle.infra.storage.bucket=my-bucket")
                .run(context -> {
                    assertThat(context).getBean(ObjectStorage.class).isInstanceOf(MinioObjectStorage.class);
                    assertThat(context).getBean(MinioClient.class).isSameAs(client);
                });
    }

    @Test
    void serviceMinioClientWithClientPropertiesFails() {
        MinioClient client = MinioClient.builder().endpoint("http://localhost:9000").credentials("ak", "sk").build();
        runner.withBean(MinioClient.class, () -> client)
                .withPropertyValues(
                        "iturtle.infra.storage.enabled=true",
                        "iturtle.infra.storage.type=minio",
                        "iturtle.infra.storage.bucket=my-bucket",
                        "iturtle.infra.storage.endpoint=http://other:9000")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().isInstanceOf(InvalidConfigurationException.class)
                        .hasMessageContaining("MinioClient 빈이 이미 있으므로"));
    }

    @Test
    void serviceBeanUsed() {
        ObjectStorage custom = mock(ObjectStorage.class);
        runner.withBean(ObjectStorage.class, () -> custom)
                .withPropertyValues(
                        "iturtle.infra.storage.enabled=true",
                        "iturtle.infra.storage.type=s3",
                        "iturtle.infra.storage.bucket=my-bucket",
                        "iturtle.infra.storage.region=ap-northeast-2")
                .run(context -> assertThat(context).getBean(ObjectStorage.class).isSameAs(custom));
    }
}
