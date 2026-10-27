# common-infra

인프라(오브젝트 스토리지 등)를 사용하는 서비스용 공통 코드. `common-core` 를 포함한다.

## 규칙

- 인프라마다 모듈을 나누지 않고 이 모듈 하나에 패키지로 나눈다 (`com.iturtle.infra.<인프라>`).
- 인프라 SDK(AWS SDK 등)는 `compileOnly` 로 두어 서비스가 쓰는 인프라의 SDK 만 직접 추가한다.
  모든 서비스가 모든 SDK 를 받지 않도록 `implementation` / `api` 로 두지 않는다.
  SDK 버전은 Spring Boot BOM 이 관리하지 않으므로 `gradle.properties` 에서 관리한다 (`awsSdkVersion`, `minioVersion`).
- 구현을 추가할 때 자동 설정은 인프라별로 클래스를 나누고 `@ConditionalOnClass`(SDK) + `@ConditionalOnProperty`
  (`iturtle.infra.<인프라>.*`) 로 켠다. 한 클래스에 모으면 SDK 가 없는 서비스에서 클래스 로딩이 깨진다.
  서비스가 같은 타입의 빈을 직접 등록하면 그것을 쓴다(`@ConditionalOnMissingBean`).
- 서비스는 SDK 의존성 추가와 properties 설정을 둘 다 해야 한다. 하나만 하면 빈이 만들어지지 않는다.

### 스토리지

- `iturtle.infra.storage.enabled=true` 와 `type`(`s3` | `minio`)을 설정하면 `ObjectStorage` 가 등록된다.
  `type` 을 비우면 아무것도 등록되지 않는다. 설정 예시는 `StorageProperties` javadoc 참고.

  | type | 서비스가 추가할 SDK | 구현 | 필수 설정 |
  |---|---|---|---|
  | `s3` | `software.amazon.awssdk:s3` | `S3ObjectStorage` | `bucket` (region / 자격 증명은 비우면 AWS 기본 설정) |
  | `minio` | `io.minio:minio` | `MinioObjectStorage` | `bucket`, `endpoint`, `access-key`, `secret-key` |

- 클라이언트(S3Client / MinioClient) 설정은 한 곳에서만 한다. 계정이 여러 개일 수 있으므로 두 설정을 합치지 않는다.
  - 클라이언트 빈이 이미 있으면(Spring Cloud AWS `spring.cloud.aws.*`, 서비스 직접 등록) 그것을 쓴다.
    이때 `iturtle.infra.storage` 에는 `enabled`, `type`, `bucket` 만 적는다. `region` / `endpoint` / `access-key` /
    `secret-key` 를 적으면 시작에 실패한다. S3Client 를 직접 등록하면 S3Presigner 도 등록한다.
  - 없으면 `iturtle.infra.storage` 설정으로 만든다. S3 자동 설정은 Spring Cloud AWS 자동 설정 다음에 실행되어
    그쪽 빈을 먼저 본다.
- 필수 설정이 없거나 충돌하면 애플리케이션 시작 시 `InvalidConfigurationException`(common-core)으로 실패한다.
- 모든 실패는 `StorageException`(`BusinessException`, `C010` 502)으로 던진다. 호출한 쪽은 인프라와 무관하게 이것만 잡고,
  잡지 않으면 전역 예외 핸들러가 502 로 응답한다.
- 버킷은 미리 만들어 둔다. 구현체는 버킷을 만들지 않는다.

## 패키지 / 클래스

### `com.iturtle.infra.objectstorage`

| 클래스 | 책임 |
|---|---|
| `ObjectStorage` | 파일 업로드 / 다운로드 / 삭제 / 존재 확인, presigned 업로드 / 다운로드 URL 생성 (인터페이스) |
| `StorageProperties` | `iturtle.infra.storage.*` 설정 (enabled, type, bucket, region, endpoint, access-key, secret-key) |
| `StorageType` | 구현체 종류 (`S3`, `MINIO`) |
| `StorageException` | 스토리지 요청 실패 (`BusinessException`, `CommonErrorCode.EXTERNAL_API_ERROR`). 구현체가 SDK 예외를 감싼다 |

### `com.iturtle.infra.objectstorage.dto`

| 클래스 | 책임 |
|---|---|
| `StorageUploadCommand` | 올릴 파일 (`storageKey`, `content`, `sizeBytes`, `contentType`, `originalFilename`) |
| `StorageUploadResult` | 올린 파일 정보 (`storageKey`, `originalFilename`, `contentType`, `sizeBytes`). `StorageEntity` 와 같은 필드 |

### `com.iturtle.infra.objectstorage.s3` / `com.iturtle.infra.objectstorage.minio`

| 클래스 | 책임 |
|---|---|
| `S3ObjectStorage` | AWS SDK(`S3Client`, `S3Presigner`)로 구현 |
| `MinioObjectStorage` | MinIO SDK(`MinioClient`)로 구현 |

### `com.iturtle.infra.autoconfigure`

| 클래스 | 책임 |
|---|---|
| `S3StorageAutoConfiguration` | AWS SDK 가 있고 `type=s3` 일 때 `S3ObjectStorage` 등록. S3Client 빈이 없을 때만 `S3Client` / `S3Presigner` 를 만든다 |
| `MinioStorageAutoConfiguration` | MinIO SDK 가 있고 `type=minio` 일 때 `MinioObjectStorage` 등록. MinioClient 빈이 없을 때만 `MinioClient` 를 만든다 |
| `S3StorageConfigurations` / `MinioStorageConfigurations` | 클라이언트 빈이 있을 때(`ProvidedClient`)와 없을 때(`OwnClient`)의 설정. 이 순서로 가져온다 |
| `StoragePropertiesChecks` | 필수 설정 / 클라이언트 설정 충돌 검사 |
