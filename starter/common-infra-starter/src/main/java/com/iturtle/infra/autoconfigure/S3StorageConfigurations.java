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

        private static StaticCredentialsProvider credentials(StorageProperties properties) {
            String secretKey = StoragePropertiesChecks.required(properties.getSecretKey(), "secret-key");
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.getAccessKey(), secretKey));
        }
    }
}
