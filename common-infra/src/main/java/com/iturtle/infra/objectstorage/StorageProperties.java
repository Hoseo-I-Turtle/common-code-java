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
 *       type: s3                     # s3 | garage | minio(새로 쓰지 않음)
 *       bucket: my-bucket
 *       region: ap-northeast-2       # s3: 비우면 AWS 기본 설정(AWS_REGION 등)을 따른다 / garage: 비우면 garage
 *       endpoint:                    # s3: 비움(S3 호환 저장소면 그 주소) / garage · minio: 필수 (예: http://localhost:3900)
 *       access-key: ${STORAGE_ACCESS_KEY}  # s3: 비우면 AWS 기본 자격 증명(IAM Role 등) / garage · minio: 필수
 *       secret-key: ${STORAGE_SECRET_KEY}
 *       path-style-access: false     # s3: endpoint 로 S3 호환 저장소에 붙을 때 true / garage: 늘 path-style (설정 무시)
 * </pre>
 * Garage 예:
 * <pre>
 * iturtle.infra.storage:
 *   enabled: true
 *   type: garage
 *   bucket: iturtle-files
 *   endpoint: http://garage:3900
 *   access-key: ${STORAGE_ACCESS_KEY}
 *   secret-key: ${STORAGE_SECRET_KEY}
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

    /**
     * s3: endpoint 로 S3 호환 저장소(MinIO, Ceph 등)에 붙을 때 true. 주소를 버킷.host 대신 host/버킷 으로 만든다
     */
    private boolean pathStyleAccess = false;

}
