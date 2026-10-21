# common-spring-web

MVC / WebFlux 가 함께 쓰는 웹 공통 코드. `common-spring-core` 를 포함한다.
서비스는 이 모듈 대신 스택 모듈(`common-spring-webmvc`)을 추가한다.

## 규칙

- 스택 중립을 유지한다. Servlet(`jakarta.servlet`), WebFlux(`reactor`, `ServerWebExchange`) 타입에 의존하지 않고
  `spring-web` 공통 타입만 사용한다. 스택 전용 코드는 각 스택 모듈에 둔다.
- 공통 로직은 추상 클래스(`Abstract*`)나 요청 객체와 무관한 클래스로 만들고, 스택 모듈이 상속하거나 값을 넘겨 사용한다.
- 응답은 항상 `ApiBaseResponse` 로 감싼다. 실패 응답은 전역 예외 핸들러만 만들고, 컨트롤러는 예외를 던진다.
- 응답과 로그에 사용자가 보낸 입력값을 그대로 남기지 않는다 (검증 실패 응답에 입력값 없음, 로그 본문은 마스킹).
- MDC 키와 로그 필드 이름은 ECS 필드 이름을 따른다 (`http.request.method`, `url.path` 등).
- 로그 레벨: 5xx 는 error(스택 트레이스 포함), 4xx 는 warn, 404 는 debug.
- 로거 이름: 예외는 `com.iturtle.error`, 접근 로그는 `com.iturtle.access`.

## 패키지 / 클래스

### `com.iturtle.spring.web.dto.response`

| 클래스 | 책임 |
|---|---|
| `ApiBaseResponse` | 공통 응답 형식 (`success`, `data`, `error`) |
| `ErrorResponse` | 실패 정보 (`status`, `code`, `message`, `errors`) |
| `FieldErrorResponse` | 검증 실패 항목 (`field`, `reason`) |

### `com.iturtle.spring.web.exception`

| 클래스 | 책임 |
|---|---|
| `AbstractGlobalExceptionHandler` | 스택 공통 예외 처리. `BusinessException`, 파라미터 제약 위반, 상태 코드를 가진 Spring 예외(`ErrorResponse`), 그 외 예외(500). 하위 클래스용 응답 / 로그 헬퍼 제공 |

### `com.iturtle.spring.web.logging`

| 클래스 | 책임 |
|---|---|
| `AccessLogProperties` | `iturtle.web.access-log.*` 설정 |
| `AccessLogWriter` | 접근 로그 한 줄 기록, 제외 경로 판정, 쿼리스트링 / 본문 마스킹 |
| `AccessLogEntry` | 스택별 필터가 요청에서 꺼내 `AccessLogWriter` 에 넘기는 값 |
| `WebMdcKeys` | 웹 요청 공통 MDC 키 |
