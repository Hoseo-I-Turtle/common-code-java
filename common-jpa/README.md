# common-jpa

JPA 를 사용하는 서비스용 공통 코드. `common-core` 를 포함한다.

## 규칙

- DB 를 사용하는 서비스만 의존하므로, 웹이나 JPA 와 무관한 코드는 두지 않는다.
- Jakarta Persistence / Spring Data JPA 는 `compileOnly` 로 두어 서비스의 버전을 따른다.
  서비스에 `spring-boot-starter-data-jpa` 가 있어야 한다.
- 엔티티는 필요한 기록 수준에 맞는 상위 클래스 하나를 상속한다. 값은 JPA Auditing 이 채우므로 직접 설정하지 않는다.
  ```
  CreatedAtEntity                 createdAt                  ← 수정 없는 기록성 데이터(로그, 이력)
   └ TimeEntity                   + updatedAt
      ├ SoftDeleteTimeEntity      + deletedAt
      └ AuditEntity               + createdBy, updatedBy
         └ SoftDeleteAuditEntity  + deletedAt, deletedBy
  ```
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

### `com.iturtle.jpa.auditing`

| 클래스 | 책임 |
|---|---|
| `SecurityAuditorAware` | 로그인 사용자 이름을 생성자 / 수정자 / 삭제자로 제공 (Spring Security 가 있을 때만 등록) |

### `com.iturtle.jpa.autoconfigure`

| 클래스 | 책임 |
|---|---|
| `CommonJpaAutoConfiguration` | JPA Auditing 을 켜고, `AuditorAware` 빈이 없으면 `SecurityAuditorAware` 를 등록한다 |
