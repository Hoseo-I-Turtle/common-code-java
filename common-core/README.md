# common-core

프레임워크에 의존하지 않는 순수 Java 공통 코드. 다른 모든 모듈이 `api` 로 포함한다.

## 규칙

- JDK 와 Lombok 외의 의존성을 추가하지 않는다. Spring, Jakarta 등 프레임워크 타입은 상위 모듈에 둔다.
- HTTP 상태처럼 프레임워크 타입이 필요한 값은 기본 타입(`int`)으로 표현한다.
- 유틸 클래스는 `final` + `private` 생성자 + `static` 메서드로 만들고 상태를 갖지 않는다.
- 도메인 에러는 서비스에서 `ErrorCode` 를 구현한 enum 으로 정의한다. `C` 접두사 코드는 `CommonErrorCode` 전용이다.
- `CommonErrorCode` 의 상태 코드는 항목마다 달라야 한다. 전역 예외 핸들러가 상태 코드로 항목을 찾는다.
  같은 상태 코드를 세분화하는 코드(예: 401 의 토큰 만료 / 위조)는 해당 모듈의 별도 enum 에 둔다.
- 응답 메시지는 항상 `ErrorCode` 의 메시지다. `BusinessException` 에 상세 메시지를 넘기면 로그에만 남는다.

## 패키지 / 클래스

### `com.iturtle.core.exception`

| 클래스 | 책임 |
|---|---|
| `ErrorCode` | 에러 코드 규약 (`status`, `code`, `message`) |
| `CommonErrorCode` | 모든 서비스가 공유하는 공통 에러 코드 (`C001` ~ `C010`, `C999`). `fromStatus` 로 상태 코드 → 코드 변환 |
| `BusinessException` | 비즈니스 예외 최상위 클래스. `ErrorCode` 와 로그용 상세 메시지를 담아 전역 예외 핸들러가 응답을 만들게 한다 |
| `InvalidConfigurationException` | 설정 누락 / 오류 / 충돌 (`IllegalStateException`). 애플리케이션 시작 시 던진다. 메시지에 설정 키와 고치는 방법을 적는다 |

### `com.iturtle.core.util`

| 클래스 | 책임 |
|---|---|
| `IdGenerateUtils` | ID 생성. UUID v7(시간순 정렬) / v4, 길이별 랜덤 hex 문자열 |
| `MaskingUtils` | 개인정보 마스킹 (이름, 전화번호, 이메일, 주민등록번호, 카드번호, 계좌번호, 범위 지정) |
| `HashUtils` | 단방향 해시. 토큰 원문 대신 저장할 SHA-256 hex(`sha256Hex`)와 비교(`matchesSha256Hex`, 시간 일정). 비밀번호에는 쓰지 않는다. 스트림 SHA-256(`sha256Hex(InputStream)`, 파일 무결성), HMAC-SHA256 서명 · 검증(`hmacSha256Hex`, `matchesHmacSha256Hex`, 웹훅 서명), 시간 일정 비교(`constantTimeEquals`) |
| `OrderKeyUtils` | 끌어서 순서를 바꾸는 목록의 문자열 순서 키 (fractional indexing). `between(앞, 뒤)` 로 옮긴 행 하나만 바꾼다. base36 소문자라 대소문자 무시 정렬(MySQL `utf8mb4_0900_ai_ci`)에서도 순서가 같다. 키가 길어지면 `evenly(n)` 으로 다시 매긴다 |

### `com.iturtle.core.file`

| 클래스 | 책임 |
|---|---|
| `FileTypePolicy` | 올린 파일을 받아도 되는지 판단 (`check(이름, 앞부분)`). 차단 확장자 → 실행 파일 내용 → 확장자와 내용 형식 불일치 순으로 거절한다. 기본 차단 목록 `DEFAULT_BLOCKED_EXTENSIONS`, 앞부분 길이 `HEAD_BYTES`(512) |
| `FileTypeCheck` | 판단 결과 (`allowed`, `reason`, `extension`, `detected`). 저장할 MIME(`contentType`, 모르면 `application/octet-stream`), 브라우저에서 열어도 되는지(`inlineViewable`: 이미지 · PDF 만, SVG 는 열지 않음) |
| `FileSignature` | 매직 넘버로 알 수 있는 형식 (이미지, PDF, ZIP 계열(docx · hwpx 등), OLE2(doc · hwp 등), 압축, 미디어, 실행 파일 · 스크립트, SVG)과 그 형식의 확장자 |

- 확장자와 올린 쪽이 보낸 Content-Type 을 믿지 않고 내용과 대조한다. Apache Tika 같은 라이브러리 대신 필요한 형식의 시그니처만 둔다 (의존성 없음).
- 압축 파일 안은 검사하지 않는다. 크기 제한과 쓰임별 허용 형식(프로필 사진은 이미지만 등)은 서비스가 정한다.

### `com.iturtle.core.retry`

| 클래스 | 책임 |
|---|---|
| `BackoffPolicy` | 실패한 작업의 다음 시도 시각. 간격이 `multiplier` 배씩 늘고 `maxDelay` 를 넘지 않으며, `maxAttempts` 번 시도하면 그만둔다 (`exponential`, `fixed`, `nextAttemptAt`, `exhausted`). common-jpa `RetryableJobEntity` · `OutboxEntity` 가 쓴다 |

### `com.iturtle.core.strategy`

| 클래스 | 책임 |
|---|---|
| `Strategy` | 키로 선택되는 전략 규약. 구현체가 처리할 키(`key()`)를 밝힌다 |
| `StrategyRegistry` | 키 → 전략 조회. 생성 시 null / 중복 키는 `InvalidConfigurationException`. `get` 은 없는 키에 `IllegalArgumentException`(구현체 누락 = 코드 버그), `find` 는 `Optional` |
