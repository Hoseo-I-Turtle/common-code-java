# common-spring-web

MVC / WebFlux 가 함께 쓰는 웹 공통 코드. `common-spring-core` 를 포함한다.
서비스는 이 모듈 대신 스택 모듈(`common-spring-webmvc`)을 추가한다.

## 규칙

- 스택 중립을 유지한다. Servlet(`jakarta.servlet`), WebFlux(`reactor`, `ServerWebExchange`) 타입에 의존하지 않고
  `spring-web` 공통 타입만 사용한다. 스택 전용 코드는 각 스택 모듈에 둔다.
- 공통 로직은 추상 클래스(`Abstract*`)나 요청 객체와 무관한 클래스로 만들고, 스택 모듈이 상속하거나 값을 넘겨 사용한다.
- 응답은 항상 `ApiBaseResponse` 로 감싼다. 실패 응답은 전역 예외 핸들러만 만들고, 컨트롤러는 예외를 던진다.
- 응답과 로그에 사용자가 보낸 입력값을 그대로 남기지 않는다 (검증 실패 응답에 입력값 없음, 로그 본문은 마스킹).
  외부 호출 로그에는 쿼리스트링, 외부 오류 로그에는 상대 서버 응답 본문을 남기지 않는다.
- 실패 응답에는 traceId 를 담아 응답만으로 로그를 찾을 수 있게 한다.
- 외부 서비스 호출 실패(`RestClientException`)는 우리 서버의 문제로 보고 502 `C010` 으로 응답한다.
  상대 서버의 4xx 를 그대로 전달해야 하면 호출부에서 잡아 `BusinessException` 으로 바꾼다.
- 컨트롤러 안의 Security 예외는 전역 예외 핸들러가 `ExceptionTranslationFilter` 와 같은 기준으로 판단한다.
  인증 실패, 또는 로그인하지 않은(익명 / remember-me) 사용자의 권한 거부는 401, 로그인한 사용자의 권한 거부는 403.
  예외를 필터로 다시 던지지 않는 이유는, 바깥 Security 필터가 상태를 정하기 전에 접근 로그가 500 으로 기록되기 때문이다.
- 선택 의존성(`jakarta.validation`, Spring Data, Spring Security)은 `compileOnly` 로 두고, 없는 서비스에서도 클래스 로딩이 깨지지 않게
  해당 타입을 쓰는 코드를 분리한다.
- MDC 키와 로그 필드 이름은 ECS 필드 이름을 따른다 (`http.request.method`, `url.path` 등).
- 로그 레벨: 5xx 는 error(스택 트레이스 포함), 4xx 는 warn, 404 는 debug.
- 로거 이름: 예외는 `com.iturtle.error`, 접근 로그는 `com.iturtle.access`, 외부 호출 로그는 `com.iturtle.client`.
- `spring.http.client.connect-timeout`(3s), `read-timeout`(10s) 기본값을 가장 낮은 우선순위로 넣는다. 서비스 설정이 항상 우선한다.

## 패키지 / 클래스

### `com.iturtle.spring.web.dto.response`

| 클래스 | 책임 |
|---|---|
| `ApiBaseResponse` | 공통 응답 형식 (`success`, `data`, `error`) |
| `ErrorResponse` | 실패 정보 (`status`, `code`, `message`, `errors`, `traceId`) |
| `FieldErrorResponse` | 검증 실패 항목 (`field`, `reason`) |
| `PageResponse` | 페이지 응답. Spring Data `Page` 변환 지원 |

### `com.iturtle.spring.web.exception`

| 클래스 | 책임 |
|---|---|
| `AbstractGlobalExceptionHandler` | 스택 공통 예외 처리. `BusinessException`, 검증 실패(컨트롤러 파라미터 / 서비스 `@Validated`), 외부 호출 실패(502), Security 예외(401 / 403), 상태 코드를 가진 Spring 예외(`ErrorResponse`), 그 외 예외(500). 하위 클래스용 응답 / 로그 헬퍼 제공 |

### `com.iturtle.spring.web.validation`

| 클래스 | 책임 |
|---|---|
| `Phone` / `PhoneValidator` | 전화번호 형식 검증 |
| `EnumValue` / `EnumValueValidator` | 문자열이 enum 상수 이름 중 하나인지 검증 |

### `com.iturtle.spring.web.client`

| 클래스 | 책임 |
|---|---|
| `ClientLogInterceptor` | RestClient 외부 호출 로그 한 줄 기록 |
| `HttpClientDefaultsEnvironmentPostProcessor` | RestClient / RestTemplate 기본 타임아웃 지정 |

### `com.iturtle.spring.web.logging`

| 클래스 | 책임 |
|---|---|
| `AccessLogProperties` | `iturtle.web.access-log.*` 설정 |
| `AccessLogWriter` | 접근 로그 한 줄 기록 (느린 요청은 warn), 제외 경로 판정, 쿼리스트링 / 본문 마스킹 |
| `AccessLogEntry` | 스택별 필터가 요청에서 꺼내 `AccessLogWriter` 에 넘기는 값 |
| `WebMdcKeys` | 웹 요청 공통 MDC 키 (`traceId` 포함) |
