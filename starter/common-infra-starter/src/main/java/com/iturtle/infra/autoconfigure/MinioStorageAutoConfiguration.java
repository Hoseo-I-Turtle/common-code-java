package com.iturtle.infra.autoconfigure;

import com.iturtle.infra.objectstorage.StorageProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * iturtle.infra.storage.enabled=true, type=minio 이고 MinIO SDK 가 있을 때 MinioObjectStorage 를 등록한다.
 * <p>
 * 클라이언트 설정은 한 곳에서만 한다.
 * <ul>
 *     <li>MinioClient 빈이 이미 있으면 그것을 쓴다. 이때 iturtle.infra.storage 의 region / endpoint / access-key /
 *     secret-key 를 설정하면 시작에 실패한다</li>
 *     <li>없으면 iturtle.infra.storage 설정으로 MinioClient 를 만든다. endpoint / access-key / secret-key 는 필수다</li>
 * </ul>
 * 서비스가 ObjectStorage 빈을 직접 등록하면 그것을 쓴다.
 */
@AutoConfiguration
@ConditionalOnClass(name = "io.minio.MinioClient")
@ConditionalOnProperty(prefix = "iturtle.infra.storage", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(StorageProperties.class)
@Import({MinioStorageConfigurations.ProvidedClient.class, MinioStorageConfigurations.OwnClient.class})
public class MinioStorageAutoConfiguration {
}
