package com.iturtle.infra.objectstorage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 오브젝트 스토리지 설정. type 에 맞는 SDK 가 있어야 ObjectStorage 가 등록된다.
 * <pre>
 * iturtle:
 *   infra:
 *     storage:
 *       enabled: true
 *       type: s3                     # s3 | minio
 *       bucket: my-bucket
 *       region: ap-northeast-2       # s3: 비우면 AWS 기본 설정(AWS_REGION 등)을 따른다
 *       endpoint:                    # s3: 비움 / minio: 필수 (예: http://localhost:9000)
 *       access-key: ${STORAGE_ACCESS_KEY}  # s3: 비우면 AWS 기본 자격 증명(IAM Role 등) / minio: 필수
 *       secret-key: ${STORAGE_SECRET_KEY}
 * </pre>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "iturtle.infra.storage")
public class StorageProperties {

    /** true 일 때만 ObjectStorage 를 등록한다 */
    private boolean enabled = false;

    private StorageType type;

    private String bucket;

    private String region;

    private String endpoint;

    private String accessKey;

    private String secretKey;

}
