package io.awspring.cloud.autoconfigure.s3;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.mockito.Mockito.mock;

/**
 * 테스트 전용. Spring Cloud AWS 의 S3AutoConfiguration 과 같은 이름으로 S3Client / S3Presigner 를 등록해
 * S3StorageAutoConfiguration 의 afterName 순서를 검증한다.
 */
@AutoConfiguration
public class S3AutoConfiguration {

    public static final S3Client CLIENT = mock(S3Client.class);

    public static final S3Presigner PRESIGNER = mock(S3Presigner.class);

    @Bean
    @ConditionalOnMissingBean
    S3Client s3Client() {
        return CLIENT;
    }

    @Bean
    @ConditionalOnMissingBean
    S3Presigner s3Presigner() {
        return PRESIGNER;
    }
}
