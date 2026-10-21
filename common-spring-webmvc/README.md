# common-spring-webmvc

Spring MVC (Servlet) 서비스용 모듈. 의존성을 추가하면 자동 설정으로 전역 예외 핸들러, 에러 컨트롤러, MDC 필터,
접근 로그 필터, 외부 호출 로그가 등록된다.
`common-spring-web` 을 포함하며, 서비스에 `spring-boot-starter-web` 이 있어야 한다.

## 규칙

- 서블릿 전용 구현만 둔다. 스택과 무관한 로직은 `common-spring-web` 으로 올린다.
- 같은 역할의 스택별 클래스와 구분되도록 이름에 스택을 붙인다 (`MvcGlobalExceptionHandler`).
- 빈은 모두 자동 설정(`CommonWebMvcAutoConfiguration`)으로 등록하고, 서비스가 같은 타입 / 이름의 빈으로 대체할 수 있게
  `@ConditionalOnMissingBean` 을 붙인다.
- 컨트롤러 밖(필터, `sendError`)의 오류도 `ApiBaseResponse` 로 응답한다. 이를 위해 Spring Boot 의 `BasicErrorController` 를
  `MvcErrorController` 로 대체한다.
- traceId 는 에러 디스패치(`/error`) 시점에 MDC 에서 사라지므로 `MdcFilter` 가 요청 속성에도 남긴다.
- 외부 호출 로그는 Spring Boot 의 `RestClient.Builder` 로 만든 RestClient 에만 적용된다. `RestClient.create()` 로 직접 만들면
  로그, 타임아웃 기본값, trace 헤더 전파가 모두 적용되지 않는다.
- 잡을 예외를 추가할 때는 `MvcGlobalExceptionHandler` 를 상속한다. 별도 advice 를 추가하면 기본 핸들러의
  `Exception` 처리가 먼저 선택될 수 있다.
- 필터 순서: Spring Security(`-100`) → `MdcFilter`(`-99`) → `AccessLogFilter`(`-98`). 인증 정보를 MDC 에 넣고,
  그 값이 접근 로그에도 포함되게 하기 위한 순서이므로 바꿀 때는 이 관계를 유지한다.
- 접근 로그는 응답의 최종 상태 코드로 한 번만 남긴다. 처리되지 않은 예외로 끝난 요청은 최종 상태가 이후 에러 디스패치(`/error`)에서
  정해지므로, 요청 정보와 MDC 를 보관했다가 에러 디스패치가 끝난 뒤 기록한다. 이를 위해 `AccessLogFilter` 는
  REQUEST / ERROR 디스패치에 모두 등록한다.
- List 설정(`exclude-paths`, `masked-fields`)은 지정하면 기본값이 통째로 교체된다. 기본값을 유지하면서 늘릴 때는
  `additional-*` 를 사용한다.

## 패키지 / 클래스

### `com.iturtle.spring.webmvc.autoconfigure`

| 클래스 | 책임 |
|---|---|
| `CommonWebMvcAutoConfiguration` | 서블릿 웹 애플리케이션에 아래 빈들을 등록한다 |

### `com.iturtle.spring.webmvc.exception`

| 클래스 | 책임 |
|---|---|
| `MvcGlobalExceptionHandler` | 공통 예외 처리에 서블릿 전용 예외(`@Valid` 실패, 타입 불일치, 본문 파싱 실패)를 더한 전역 예외 핸들러 |
| `MvcErrorController` | `/error` 응답을 `ApiBaseResponse` 로 통일 (필터 예외, `sendError`) |

### `com.iturtle.spring.webmvc.logging`

| 클래스 | 책임 |
|---|---|
| `MdcFilter` | 요청마다 기본 MDC 값과 `MdcContributor` 값을 반영하고, 요청이 끝나면 복원한다 |
| `MdcContributor` | 서비스가 요청별 MDC 값(사용자 ID 등)을 추가하는 확장 지점 |
| `AccessLogFilter` | 서블릿 요청에서 접근 로그 값을 꺼내 최종 상태 코드로 `AccessLogWriter` 에 기록한다. 에러 응답 본문을 위해 요청 본문을 캐싱한다 |
