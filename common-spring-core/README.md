# common-spring-core

웹 스택과 무관한 Spring 공통 코드. `common-core` 를 포함한다.

## 규칙

- Servlet / WebFlux 등 웹 타입에 의존하지 않는다. 웹 관련 코드는 `common-spring-web` 이하에 둔다.
- Spring 의존성은 `compileOnly` 로 두어 서비스의 Spring Boot 버전을 따른다.
- MDC 를 건드리는 코드는 자신이 넣은 key 만 복원하고, 다른 코드가 넣은 값은 건드리지 않는다.

## 패키지 / 클래스

### `com.iturtle.spring.core.mdc`

| 클래스 | 책임 |
|---|---|
| `MdcContext` | MDC 값을 모아 한 구간에만 반영하고, 구간이 끝나면 반영한 key 만 이전 값으로 복원한다. 웹 필터가 없는 배치 / 컨슈머 / 비동기 작업에서 사용 |
