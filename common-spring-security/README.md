# common-spring-security

스택과 무관한 Security 공통 코드 (JWT 발급 / 검증). `common-spring-core` 를 포함한다.
서비스는 보통 이 모듈 대신 스택 모듈(`common-spring-security-webmvc`)을 추가한다.

## 규칙

- 서블릿 / WebFlux 타입에 의존하지 않는다. 요청에서 토큰을 꺼내는 필터 등은 스택 모듈에 둔다.
- JWT 는 이 모듈의 주 기능이므로 nimbus-jose-jwt 를 `api` 로 전달한다. 버전은 Spring Boot BOM 이 관리하지 않으므로
  Spring Security 가 쓰는 버전에 맞춰 `gradle.properties` 의 `nimbusJoseJwtVersion` 으로 관리한다.
- JWT 는 opt-in 이다. `iturtle.security.jwt.enabled=true` 일 때만 `JwtProviders` 가 등록된다.
- 토큰 종류(access, refresh 등)마다 서명 방식 / 키 / 유효 기간 / issuer 를 따로 설정한다(`iturtle.security.jwt.tokens.<이름>`).
  서비스는 자신이 다루는 토큰만 설정한다. 예를 들어 refresh 토큰은 인증 서비스만 설정한다.
- 서명 방식 기본값은 HS256 이며, 서비스가 여럿이면 RS256 을 권장한다.
  - HS256: secret 을 가진 모든 서비스가 발급할 수 있다. secret 은 32바이트 이상.
  - RS256: private key 를 설정한 서비스(인증 서비스)만 발급하고, 나머지는 public key 로 검증만 한다.
- 토큰 헤더 `typ` 에 토큰 이름(`access+jwt` 등)을 넣고 검증 시 확인한다. 같은 키를 쓰더라도 다른 종류의 토큰으로 쓸 수 없다.
- 검증 시 토큰 헤더의 alg 를 믿지 않고 설정된 방식만 허용한다 (alg 바꿔치기 방지).
- 키 설정이 잘못되면 애플리케이션 시작 시 실패한다. 모든 토큰 설정을 시작할 때 검증한다.
- 검증 실패는 `InvalidJwtException`(`BusinessException`)으로 던진다. 만료는 `J001`, 그 외는 `J002` 로 구분해
  클라이언트가 재발급 여부를 판단할 수 있게 한다.
- 표준 클레임(`sub`, `exp`, `iat`, `iss`)은 사용자 클레임으로 덮어쓸 수 없다.
- 키 교체(`kid`)는 아직 지원하지 않는다. 토큰 종류당 키는 하나다.

## 패키지 / 클래스

### `com.iturtle.spring.security.jwt`

| 클래스 | 책임 |
|---|---|
| `JwtProviders` | 토큰 이름별 `JwtProvider` 묶음. 이름으로 꺼낸다 |
| `JwtProvider` | 토큰 한 종류의 발급 / 검증 (typ, 서명, 만료, nbf, issuer) |
| `JwtProperties` | `iturtle.security.jwt.*` 설정 (토큰별 서명 방식 / 키 / 유효 기간 / issuer, 시간 오차) |
| `JwtAlgorithm` | 서명 방식 (`HS256`, `RS256`) |
| `JwtErrorCode` | 토큰 검증 실패 코드 (`J001` 만료, `J002` 그 외) |
| `InvalidJwtException` | 토큰 검증 실패 예외 |

### `com.iturtle.spring.security.autoconfigure`

| 클래스 | 책임 |
|---|---|
| `CommonSecurityAutoConfiguration` | JWT 가 켜져 있으면 `JwtProviders` 를 등록한다 |
