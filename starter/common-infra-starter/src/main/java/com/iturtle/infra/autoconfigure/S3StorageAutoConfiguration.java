package com.iturtle.infra.autoconfigure;

import com.iturtle.infra.objectstorage.StorageProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * iturtle.infra.storage.enabled=true, type=s3 이고 AWS SDK(S3)가 있을 때 S3ObjectStorage 를 등록한다.
 * <p>
 * 클라이언트 설정은 한 곳에서만 한다.
 * <ul>
 *     <li>S3Client 빈이 이미 있으면(Spring Cloud AWS, 서비스 직접 등록) 그것을 쓴다. 이때 iturtle.infra.storage 의
 *     region / endpoint / access-key / secret-key 를 설정하면 시작에 실패한다</li>
 *     <li>없으면 iturtle.infra.storage 설정으로 S3Client / S3Presigner 를 만든다. 비우면 AWS 기본 설정(환경 변수, IAM Role 등)을 따른다</li>
 * </ul>
 * Spring Cloud AWS 의 빈을 먼저 보도록 그 자동 설정 다음에 실행한다. 서비스가 ObjectStorage 빈을 직접 등록하면 그것을 쓴다.
 */
@AutoConfiguration(afterName = "io.awspring.cloud.autoconfigure.s3.S3AutoConfiguration")
@ConditionalOnClass(name = "software.amazon.awssdk.services.s3.S3Client")
@ConditionalOnProperty(prefix = "iturtle.infra.storage", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(StorageProperties.class)
@Import({S3StorageConfigurations.ProvidedClient.class, S3StorageConfigurations.OwnClient.class})
public class S3StorageAutoConfiguration {
}
