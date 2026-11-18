package com.iturtle.infra.autoconfigure;

import com.iturtle.core.exception.InvalidConfigurationException;
import com.iturtle.infra.objectstorage.ObjectStorage;
import com.iturtle.infra.objectstorage.StorageProperties;
import com.iturtle.infra.objectstorage.s3.S3ObjectStorage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * S3StorageAutoConfiguration 이 순서대로 가져온다. ProvidedClient 를 먼저 평가해야 OwnClient 가 만든 S3Client 를
 * 이미 있던 빈으로 오인하지 않는다.
 */
final class S3StorageConfigurations {

    private S3StorageConfigurations() {
    }

    /**
     * S3Client 빈이 이미 있을 때: 그것을 쓰고, iturtle.infra.storage 의 클라이언트 설정은 허용하지 않는다.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "iturtle.infra.storage", name = "type", havingValue = "s3")
    @ConditionalOnBean(S3Client.class)
    static class ProvidedClient {

        @Bean
        @ConditionalOnMissingBean
        ObjectStorage objectStorage(S3Client client, ObjectProvider<S3Presigner> presigner,
                                    StorageProperties properties) {
            StoragePropertiesChecks.rejectClientProperties(properties, "S3Client");
            S3Presigner provided = presigner.getIfAvailable();
            if (provided == null) {
                throw new InvalidConfigurationException("S3Client 빈을 직접 등록하면 S3Presigner 빈도 등록해야 한다");
            }
            return new S3ObjectStorage(client, provided, StoragePropertiesChecks.required(properties.getBucket(), "bucket"));
        }
    }

    /**
     * S3Client 빈이 없을 때: iturtle.infra.storage 설정으로 S3Client / S3Presigner 를 만든다.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "iturtle.infra.storage", name = "type", havingValue = "s3")
    @ConditionalOnMissingBean(S3Client.class)
    static class OwnClient {

        @Bean
        S3Client s3Client(StorageProperties properties) {
            S3ClientBuilder builder = S3Client.builder();
            if (StringUtils.hasText(properties.getRegion())) {
                builder.region(Region.of(properties.getRegion()));
            }
            if (StringUtils.hasText(properties.getEndpoint())) {
                builder.endpointOverride(URI.create(properties.getEndpoint()));
                checksumsOnlyWhenRequired(builder);
            }
            if (StringUtils.hasText(properties.getAccessKey())) {
                builder.credentialsProvider(credentials(properties));
            }
            if (properties.isPathStyleAccess()) {
                builder.forcePathStyle(true);
            }
            return builder.build();
        }

        @Bean
        @ConditionalOnMissingBean
        S3Presigner s3Presigner(StorageProperties properties) {
            S3Presigner.Builder builder = S3Presigner.builder();
            if (StringUtils.hasText(properties.getRegion())) {
                builder.region(Region.of(properties.getRegion()));
            }
            if (StringUtils.hasText(properties.getEndpoint())) {
                builder.endpointOverride(URI.create(properties.getEndpoint()));
            }
            if (StringUtils.hasText(properties.getAccessKey())) {
                builder.credentialsProvider(credentials(properties));
            }
            if (properties.isPathStyleAccess()) {
                builder.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
            }
            return builder.build();
        }

        @Bean
        @ConditionalOnMissingBean
        ObjectStorage objectStorage(S3Client client, S3Presigner presigner, StorageProperties properties) {
            return new S3ObjectStorage(client, presigner, StoragePropertiesChecks.required(properties.getBucket(), "bucket"));
        }

        /**
         * S3 호환 저장소(Garage, SeaweedFS 등)는 AWS SDK 2.30 부터 기본으로 붙는 요청 체크섬(aws-chunked 끝 체크섬)을 받지 못해
         * 업로드가 실패한다. 엔드포인트를 지정했으면 체크섬은 API 가 요구할 때만 보내고, 응답 체크섬도 그때만 검사한다.
         * 2.30 보다 오래된 SDK 는 이 설정이 없고 원래 체크섬을 보내지 않으므로 건너뛴다.
         */
        static void checksumsOnlyWhenRequired(S3ClientBuilder builder) {
            try {
                builder.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED);
                builder.responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
            } catch (NoSuchMethodError | NoClassDefFoundError e) {
                // AWS SDK 2.30 미만
            }
        }

        private static StaticCredentialsProvider credentials(StorageProperties properties) {
            String secretKey = StoragePropertiesChecks.required(properties.getSecretKey(), "secret-key");
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.getAccessKey(), secretKey));
        }
    }

    /**
     * type=garage: Garage(S3 호환 자체 호스팅 저장소)용 S3Client / S3Presigner 를 <b>빈으로 등록하지 않고</b> 안에서 만든다.
     * 빈으로 등록하면 Spring Cloud AWS 의 S3Client 자동 설정(AWS S3 용)이 꺼지기 때문이다. 클라이언트는 ObjectStorage 빈이
     * 닫힐 때 함께 닫힌다.
     * <ul>
     *     <li>endpoint · access-key · secret-key · bucket 필수</li>
     *     <li>region 을 비우면 {@code garage} (Garage 기본 s3_region)</li>
     *     <li>path-style 주소, 요청 · 응답 체크섬은 필요할 때만 (Garage 는 AWS SDK 기본 끝 체크섬을 받지 못함)</li>
     * </ul>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "iturtle.infra.storage", name = "type", havingValue = "garage")
    static class Garage {

        static final String DEFAULT_REGION = "garage";

        @Bean
        @ConditionalOnMissingBean
        ObjectStorage objectStorage(StorageProperties properties) {
            URI endpoint = URI.create(StoragePropertiesChecks.required(properties.getEndpoint(), "endpoint"));
            String bucket = StoragePropertiesChecks.required(properties.getBucket(), "bucket");
            StaticCredentialsProvider credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(
                    StoragePropertiesChecks.required(properties.getAccessKey(), "access-key"),
                    StoragePropertiesChecks.required(properties.getSecretKey(), "secret-key")));
            Region region = Region.of(StringUtils.hasText(properties.getRegion()) ? properties.getRegion() : DEFAULT_REGION);

            S3ClientBuilder clientBuilder = S3Client.builder()
                    .endpointOverride(endpoint)
                    .region(region)
                    .credentialsProvider(credentials)
                    .forcePathStyle(true);
            OwnClient.checksumsOnlyWhenRequired(clientBuilder);
            S3Presigner presigner = S3Presigner.builder()
                    .endpointOverride(endpoint)
                    .region(region)
                    .credentialsProvider(credentials)
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                    .build();
            return new ClosingS3ObjectStorage(clientBuilder.build(), presigner, bucket);
        }
    }

    /**
     * 안에서 만든 클라이언트를 빈이 닫힐 때 함께 닫는다 (Spring 이 AutoCloseable 빈의 close 를 부른다).
     */
    static final class ClosingS3ObjectStorage extends S3ObjectStorage implements AutoCloseable {

        private final S3Client client;
        private final S3Presigner presigner;

        ClosingS3ObjectStorage(S3Client client, S3Presigner presigner, String bucket) {
            super(client, presigner, bucket);
            this.client = client;
            this.presigner = presigner;
        }

        @Override
        public void close() {
            presigner.close();
            client.close();
        }
    }
}
