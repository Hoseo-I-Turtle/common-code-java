# common-jpa

JPA 를 사용하는 서비스용 공통 코드. `common-core` 를 포함한다.

## 규칙

- DB 를 사용하는 서비스만 의존하므로, 웹이나 JPA 와 무관한 코드는 두지 않는다.
- Jakarta Persistence / Spring Data JPA 는 `compileOnly` 로 두어 서비스의 버전을 따른다.
  서비스에 `spring-boot-starter-data-jpa` 가 있어야 한다.
- 엔티티는 필요한 기록 수준에 맞는 상위 클래스 하나를 상속한다. 값은 JPA Auditing 이 채우므로 직접 설정하지 않는다.
  ```
  CreatedAtEntity                 createdAt                  ← 수정 없는 기록성 데이터(로그, 이력)
   ├ StorageEntity                + 파일 정보, createdBy      ← 오브젝트 스토리지 업로드 기록
   │  └ PolymorphicStorageEntity  + targetType, targetId
   ├ OutboxEntity<EVENT_TYPE>     + eventId, eventType, payload, status, attempts, publishedAt  ← transactional outbox
   │  └ PolymorphicOutboxEntity<EVENT_TYPE, AGGREGATE_TYPE>  + aggregateType, aggregateId
   ├ InboxEntity<EVENT_TYPE>      + messageId, eventType, payload, status, attempts, processedAt ← 중복 처리 방지 inbox
   └ TimeEntity                   + updatedAt
      ├ SoftDeleteTimeEntity      + deletedAt
      └ AuditEntity               + createdBy, updatedBy
         └ SoftDeleteAuditEntity  + deletedAt, deletedBy
  ```
- 업로드 기록이 여러 종류의 대상에 붙으면 `PolymorphicStorageEntity<TARGET_TYPE>` 를 상속한다 (Outbox / Inbox 의 eventType, aggregateType 도 같다). `TARGET_TYPE` 은 enum 이나 String 이며
  어느 쪽이든 문자열(enum 은 이름)로 저장한다 (Hibernate `@JdbcTypeCode`). FK 가 없으므로 대상이 삭제되어도 남은 행은 서비스가 정리하고,
  `(target_type, target_id)` 인덱스는 자식 엔티티의 `@Table` 에 선언한다.
- Outbox / Inbox 는 진행 상황을 `OutboxStatus` / `InboxStatus`(문자열 저장)와 `markPublished()` / `markProcessed()` /
  `markFailed()` 로 바꾼다. Inbox 는 `messageId` 가 unique 라 같은 메시지를 다시 저장하면 실패한다. 재시도 횟수 / 간격,
  릴레이 폴링과 잠금, 끝난 행 정리, `(status, created_at)` 인덱스는 서비스가 정한다.
  aggregate 는 보통 payload 에 있으므로 `OutboxEntity` 로 충분하다. 메시지 키로 같은 aggregate 순서를 보장하거나
  Debezium Outbox Event Router 를 쓰는 등 컬럼으로 읽어야 할 때만 `PolymorphicOutboxEntity` 를 쓴다.
- 시각은 서버 시간대와 무관하도록 `Instant`(UTC)로 저장한다.
- 생성자 / 수정자 / 삭제자는 `String` 이다. Spring Security 가 있으면 로그인 사용자의 `Authentication.getName()`
  (JWT 인증이면 `sub`)을 넣고, 로그인하지 않았거나 익명이면 null 이다. 다른 값을 넣으려면 `AuditorAware<String>` 빈을 등록한다.
- soft delete 는 `repository.delete()` 가 아니라 엔티티의 `delete()` 로 한다. `delete()` 는 삭제 시각만 기록하고,
  삭제자는 저장 직전에 리스너가 `AuditorAware` 로 채운다. 이미 삭제된 엔티티를 다시 삭제해도 처음 기록을 유지하며, `restore()` 로 되돌린다.
- 삭제된 행을 조회에서 빼는 것(`@SQLRestriction("deleted_at is null")`)은 서비스가 엔티티에 직접 붙인다.
  Hibernate 제약상 상위 클래스에 붙일 수 없고, 삭제된 데이터를 조회해야 하는 엔티티도 있기 때문이다.
- Auditing 은 자동 설정이 켠다. 서비스가 `@EnableJpaAuditing` 을 직접 선언해도 중복으로 켜지지 않는다.
  끄려면 `iturtle.jpa.auditing.enabled=false`.
- 통합 테스트는 테스트 전용(`testRuntimeOnly`) H2 로 한다. 배포물과 서비스에는 포함되지 않는다.

## 패키지 / 클래스

### `com.iturtle.jpa.entity`

| 클래스 | 책임 |
|---|---|
| `CreatedAtEntity` | `createdAt` 기록 |
| `TimeEntity` | + `updatedAt` |
| `AuditEntity` | + `createdBy`, `updatedBy` |
| `SoftDeleteTimeEntity` | `TimeEntity` + `deletedAt` (soft delete) |
| `SoftDeleteAuditEntity` | `AuditEntity` + `deletedAt`, `deletedBy` (soft delete) |
| `SoftDeletable` | soft delete 엔티티 공통 규약 (`delete()`, `restore()`, `isDeleted()`) |
| `SoftDeleteAuditingListener` | 삭제된 `SoftDeleteAuditEntity` 저장 직전에 `deletedBy` 를 채운다 |

### `com.iturtle.jpa.entity.storage`

| 클래스 | 책임 |
|---|---|
| `StorageEntity` | `CreatedAtEntity` + 오브젝트 스토리지 업로드 파일 정보(`storageKey`, `originalFilename`, `contentType`, `sizeBytes`), `createdBy` |
| `PolymorphicStorageEntity<TARGET_TYPE>` | `StorageEntity` + 대상 종류 / 식별자(`targetType`, `targetId`) |

### `com.iturtle.jpa.entity.messaging`

| 클래스 | 책임 |
|---|---|
| `OutboxEntity<EVENT_TYPE>` | `CreatedAtEntity` + 발행할 이벤트(`eventId`, `eventType: EVENT_TYPE`, `payload`)와 발행 진행 상황(`status`, `attempts`, `publishedAt`) |
| `PolymorphicOutboxEntity<EVENT_TYPE, AGGREGATE_TYPE>` | `OutboxEntity<EVENT_TYPE>` + 이벤트가 나온 aggregate 종류 / 식별자(`aggregateType: AGGREGATE_TYPE`, `aggregateId`) |
| `OutboxStatus` | `PENDING` → `PUBLISHED` / `FAILED` |
| `InboxEntity<EVENT_TYPE>` | `CreatedAtEntity` + 받은 메시지(`messageId` unique, `eventType: EVENT_TYPE`, `payload`)와 처리 진행 상황(`status`, `attempts`, `processedAt`) |
| `InboxStatus` | `RECEIVED` → `PROCESSED` / `FAILED` |

### `com.iturtle.jpa.auditing`

| 클래스 | 책임 |
|---|---|
| `SecurityAuditorAware` | 로그인 사용자 이름을 생성자 / 수정자 / 삭제자로 제공 (Spring Security 가 있을 때만 등록) |

### `com.iturtle.jpa.autoconfigure`

| 클래스 | 책임 |
|---|---|
| `CommonJpaAutoConfiguration` | JPA Auditing 을 켜고, `AuditorAware` 빈이 없으면 `SecurityAuditorAware` 를 등록한다 |
