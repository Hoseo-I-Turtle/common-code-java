# common-jpa

JPA 를 사용하는 서비스용 공통 코드. `common-core` 를 포함한다.

## 규칙

- DB 를 사용하는 서비스만 의존하므로, 웹이나 JPA 와 무관한 코드는 두지 않는다.
- Jakarta Persistence / Spring Data JPA 는 `compileOnly` 로 두어 서비스의 버전을 따른다.
  서비스에 `spring-boot-starter-data-jpa` 가 있어야 한다.

## 패키지 / 클래스

아직 구현된 코드는 없다.
