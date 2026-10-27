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
| `HashUtils` | 단방향 해시. 토큰 원문 대신 저장할 SHA-256 hex(`sha256Hex`)와 비교(`matchesSha256Hex`, 시간 일정). 비밀번호에는 쓰지 않는다 |
