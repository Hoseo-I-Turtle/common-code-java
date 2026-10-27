package com.iturtle.infra.autoconfigure;

import com.iturtle.infra.objectstorage.ObjectStorage;
import com.iturtle.infra.objectstorage.StorageProperties;
import com.iturtle.infra.objectstorage.minio.MinioObjectStorage;
import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * MinioStorageAutoConfiguration 이 순서대로 가져온다. ProvidedClient 를 먼저 평가해야 OwnClient 가 만든 MinioClient 를
 * 이미 있던 빈으로 오인하지 않는다.
 */
final class MinioStorageConfigurations {

    private MinioStorageConfigurations() {
    }

    /**
     * MinioClient 빈이 이미 있을 때: 그것을 쓰고, iturtle.infra.storage 의 클라이언트 설정은 허용하지 않는다.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "iturtle.infra.storage", name = "type", havingValue = "minio")
    @ConditionalOnBean(MinioClient.class)
    static class ProvidedClient {

        @Bean
        @ConditionalOnMissingBean
        ObjectStorage objectStorage(MinioClient client, StorageProperties properties) {
            StoragePropertiesChecks.rejectClientProperties(properties, "MinioClient");
            return new MinioObjectStorage(client, StoragePropertiesChecks.required(properties.getBucket(), "bucket"));
        }
    }

    /**
     * MinioClient 빈이 없을 때: iturtle.infra.storage 설정으로 MinioClient 를 만든다.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "iturtle.infra.storage", name = "type", havingValue = "minio")
    @ConditionalOnMissingBean(MinioClient.class)
    static class OwnClient {

        @Bean
        MinioClient minioClient(StorageProperties properties) {
            MinioClient.Builder builder = MinioClient.builder()
                    .endpoint(StoragePropertiesChecks.required(properties.getEndpoint(), "endpoint"))
                    .credentials(StoragePropertiesChecks.required(properties.getAccessKey(), "access-key"),
                            StoragePropertiesChecks.required(properties.getSecretKey(), "secret-key"));
            if (StringUtils.hasText(properties.getRegion())) {
                builder.region(properties.getRegion());
            }
            return builder.build();
        }

        @Bean
        @ConditionalOnMissingBean
        ObjectStorage objectStorage(MinioClient client, StorageProperties properties) {
            return new MinioObjectStorage(client, StoragePropertiesChecks.required(properties.getBucket(), "bucket"));
        }
    }
}
