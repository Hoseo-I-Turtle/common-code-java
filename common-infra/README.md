# common-infra

인프라(오브젝트 스토리지, Redis 등)를 사용하는 서비스용 공통 코드. `common-core` 를 포함한다.

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
- `type=s3` 로 S3 호환 저장소(MinIO, Ceph 등)에 `endpoint` 를 지정해 붙을 때는 `path-style-access=true` 로 둔다.
  기본(가상 호스트 방식)은 `버킷.host` 주소를 만들어 대개 풀리지 않는다.
- 필수 설정이 없거나 충돌하면 애플리케이션 시작 시 `InvalidConfigurationException`(common-core)으로 실패한다.
- 모든 실패는 `StorageException`(`BusinessException`, `C010` 502)으로 던진다. 호출한 쪽은 인프라와 무관하게 이것만 잡고,
  잡지 않으면 전역 예외 핸들러가 502 로 응답한다.
- 버킷은 미리 만들어 둔다. 구현체는 버킷을 만들지 않는다.

### Redis

- 연결과 `StringRedisTemplate` 은 Spring Boot(`spring-boot-starter-data-redis`, `spring.data.redis.*`)가 만든다. 이 모듈은 연결을 만들지 않는다.
- `iturtle.infra.redis.enabled=true` 이고 `StringRedisTemplate` 빈이 있으면 `RedisStore` 가 등록된다.
- `RedisStore` 의 기본 메서드는 Redis 가 응답하지 않으면 WARN 로그(`com.iturtle.error`)만 남기고 「없음」으로 답한다 (캐시처럼 없어도 되는 값).
  같은 작업의 실패는 10초에 한 번만 남기고 생략한 건수를 함께 남긴다. 스택 트레이스는 DEBUG 에서만 남긴다.
  `*OrThrow` 메서드는 `RedisUnavailableException`(`BusinessException`, `C010` 502)을 던진다. 시도 제한처럼 동시성을 지키는 값은
  실패를 「없음」으로 넘기면 제한이 풀리므로 이것을 쓰고, 부르는 쪽이 다른 길(DB 잠금 등)로 간다.
- 실패 로그와 예외 메시지의 키는 마지막 `:` 뒤(식별자 자리)를 가린다 (`auth:attempt:1234` → `auth:attempt:****`).
  그래도 접두사에 개인정보 / 비밀값을 넣지 않는다 (식별자는 마지막 자리에 두거나 `HashUtils` 로 해시).

## 테스트

- 자동 설정 / 장애 동작은 실제 서버 없이 테스트한다.
- 실제 서버 테스트(`*ContainerTest`)는 Testcontainers 로 Redis(`redis:7.4.12-alpine`)와 S3 호환 서버(`adobe/s3mock`)를 띄운다.
  Docker 가 없으면 건너뛰므로 `./gradlew build` 는 Docker 없이도 통과한다. 돌리려면 Docker 를 켠다.
- S3 / MinIO 구현 모두 S3Mock 으로 검증한다 (MinIO 공식 이미지는 공개 레지스트리에서 익명으로 받을 수 없다).
  S3Mock 은 서명을 검사하지 않으므로 presigned URL 의 서명 자체는 검증되지 않는다.

## 패키지 / 클래스

### `com.iturtle.infra.objectstorage`

| 클래스 | 책임 |
|---|---|
| `ObjectStorage` | 파일 업로드 / 다운로드 / 삭제 / 존재 확인, presigned 업로드 / 다운로드 URL 생성 (인터페이스) |
| `StorageProperties` | `iturtle.infra.storage.*` 설정 (enabled, type, bucket, region, endpoint, access-key, secret-key, path-style-access) |
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

### `com.iturtle.infra.redis`

| 클래스 | 책임 |
|---|---|
| `RedisStore` | Redis 공용 창구 (get / set / setIfAbsent / multiGet / hasKey / delete(키 하나, 여러 키를 DEL 하나로), TTL 을 붙여 여러 키를 파이프라인으로 쓰는 `multiSet`(원자적이지 않음), `*OrThrow`, TTL 과 함께 원자적으로 증가하는 `incrementOrThrow`) |
| `RedisUnavailableException` | Redis 응답 없음 (`BusinessException`, `CommonErrorCode.EXTERNAL_API_ERROR`). `*OrThrow` 만 던진다 |

### `com.iturtle.infra.autoconfigure`

| 클래스 | 책임 |
|---|---|
| `S3StorageAutoConfiguration` | AWS SDK 가 있고 `type=s3` 일 때 `S3ObjectStorage` 등록. S3Client 빈이 없을 때만 `S3Client` / `S3Presigner` 를 만든다 |
| `MinioStorageAutoConfiguration` | MinIO SDK 가 있고 `type=minio` 일 때 `MinioObjectStorage` 등록. MinioClient 빈이 없을 때만 `MinioClient` 를 만든다 |
| `S3StorageConfigurations` / `MinioStorageConfigurations` | 클라이언트 빈이 있을 때(`ProvidedClient`)와 없을 때(`OwnClient`)의 설정. 이 순서로 가져온다 |
| `StoragePropertiesChecks` | 필수 설정 / 클라이언트 설정 충돌 검사 |
| `RedisStoreAutoConfiguration` | spring-data-redis 가 있고 `StringRedisTemplate` 빈이 있으며 `iturtle.infra.redis.enabled=true` 일 때 `RedisStore` 등록 (Boot 의 `RedisAutoConfiguration` 다음) |
