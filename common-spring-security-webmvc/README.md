# common-spring-security-webmvc

Spring Security 를 쓰는 MVC 서비스용 모듈. Security 오류(401 / 403)를 `ApiBaseResponse` 로 응답하고, JWT 인증 필터를 제공한다.
`common-spring-webmvc`, `common-spring-security` 를 포함하며, 서비스에 `spring-boot-starter-security` 가 있어야 한다.

## 규칙

- Security 를 쓰는 서비스만 의존한다. Security 와 무관한 웹 공통 코드는 `common-spring-webmvc` 에 둔다.
- 서블릿 Security 전용이다. WebFlux Security 는 API 가 다르므로 별도 모듈로 만든다.
- 필터 단계의 오류(인증 실패 / 권한 없음)는 `@RestControllerAdvice` 를 거치지 않으므로 `AuthenticationEntryPoint` /
  `AccessDeniedHandler` 가 JSON 을 직접 쓴다. 서비스는 이 두 빈을 `SecurityFilterChain` 에 연결해야 한다.
- `/error` 디스패치도 Security 필터를 거치므로 `DispatcherType.ERROR` 는 `permitAll` 로 둔다. 그렇지 않으면 다른 오류 응답이
  401 로 바뀐다.
- 컨트롤러 단계의 Security 예외(`@PreAuthorize` 거부 등)는 이 모듈이 아니라 전역 예외 핸들러가 처리한다
  (`common-spring-web` README 참고). advice 를 하나로 유지하기 위해 이 모듈은 `@RestControllerAdvice` 를 두지 않는다.
- 401 / 403 은 4xx 규칙에 따라 warn 으로 남긴다. 단 토큰 만료(`J001`)는 정상적인 수명 주기이므로 debug 로 남긴다.
  위조(`J002`)와 무효화된 토큰 재사용(`J003`)은 공격 신호일 수 있어 warn 을 유지한다.
- JWT 표현(클레임 구성)은 서비스가 자유롭게 정한다. 공통 모듈은 토큰에서 `sub` 만 사용하고, 사용자 / 권한은
  서비스가 구현한 `UserDetailsService.loadUserByUsername(sub)` 가 돌려준 `UserDetails` 로 만든다.
  - principal = `UserDetails`, 권한 = `UserDetails.getAuthorities()`, details = 전체 클레임.
  - 사용자가 없거나 비활성 / 잠김 / 만료 계정이면 401 `C002` 로 응답한다 (토큰 오류 `J001` / `J002` 와 구분).
  - 요청마다 조회하므로 DB 를 쓴다면 캐시를 고려한다. 대신 권한 변경 / 계정 정지가 토큰 만료를 기다리지 않고 반영된다.
- `JwtAuthenticationFilter` 는 자동 등록하지 않는다. 서비스가 `SecurityFilterChain` 설정에서 어떤 토큰으로 인증할지 정해
  직접 만들고(`new JwtAuthenticationFilter(jwtProviders.get("access"), writer, userDetailsService)`) `addFilterBefore` 로 추가한다.
  어떤 경로가 어떤 토큰을 쓰는지가 체인 설정에 그대로 드러나게 하기 위해서다.
- `JwtAuthenticationFilter` 를 빈으로 등록하지 않는다. Filter 빈은 Spring Boot 가 서블릿 필터로도 자동 등록해
  Security 체인 밖에서 한 번 더 실행된다.
- URL 별로 토큰이 다르면 `securityMatcher` 로 체인을 나누고 체인마다 필터를 만든다. 요청마다 매칭되는 첫 체인 하나만
  실행되므로, matcher 가 없는 체인은 마지막에 두고 `exceptionHandling` 과 `/error` 허용은 해당 경로를 받는 체인마다 설정한다.
- 필터는 생성자로 받은 토큰 종류만 인증에 사용한다. 다른 종류의 토큰은 `typ` 이 달라 거부된다.
- 토큰이 없으면 인증 없이 통과시키고(인가 규칙이 판단), 토큰이 잘못되었으면 경로와 관계없이 즉시 401 로 응답한다.
  그래서 클라이언트는 공개 경로에도 만료된 토큰을 보내지 않아야 한다.
- 토큰 무효화(로그아웃)를 쓰면 `JwtBlacklistFilter` 를 같은 `JwtProvider` 로 만들어 `JwtAuthenticationFilter` 앞에 둔다
  (`addFilterBefore(blacklistFilter, JwtAuthenticationFilter.class)`). 무효화된 토큰으로 `UserDetailsService` 를 호출하지 않기 위해서다.
  - 다른 필터에 기대지 않도록 토큰을 직접 검증한 뒤 블랙리스트를 확인한다. 토큰이 없거나 잘못되었으면 판단하지 않고 넘긴다.
  - 무효화된 토큰이면 401 `J003`.
  - 로그아웃은 `JwtAuthenticationFilter` 가 `Authentication` details 에 넣은 클레임(`JWTClaimsSet`)으로 `TokenBlacklist.revoke` 를 호출한다.

## 패키지 / 클래스

### `com.iturtle.spring.security.webmvc`

| 클래스 | 책임 |
|---|---|
| `ApiAuthenticationEntryPoint` | 필터 단계 인증 실패 → 401 `C002` |
| `ApiAccessDeniedHandler` | 필터 단계 권한 없음 → 403 `C003` |
| `SecurityErrorResponseWriter` | 필터 단계 오류를 `ApiBaseResponse` JSON 으로 응답에 쓴다 (traceId 포함) |
| `JwtAuthenticationFilter` | `Authorization: Bearer` 토큰을 지정한 토큰 종류로 검증하고, `sub` 로 조회한 `UserDetails` 를 SecurityContext 에 넣는다 |
| `JwtBlacklistFilter` | `TokenBlacklist` 에서 무효화된 토큰이면 401 `J003` 으로 응답한다 |
| `BearerTokens` | `Authorization: Bearer` 헤더에서 토큰을 꺼낸다 (JWT 필터 공용, 패키지 내부) |

### `com.iturtle.spring.security.webmvc.autoconfigure`

| 클래스 | 책임 |
|---|---|
| `CommonSecurityWebMvcAutoConfiguration` | `SecurityErrorResponseWriter`, entry point, denied handler 를 등록한다. 같은 타입의 빈을 등록하면 대체된다 |
