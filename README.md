# common-code

iturtle 서비스들이 공유하는 공통 라이브러리. 응답 형식, 예외 처리, 로그, JWT 인증을 서비스마다 다시 만들지 않도록 한 곳에서 관리한다.

## 요구 사항

| 항목 | 버전 |
|---|---|
| Java | 17 이상 |
| Spring Boot | 3.4 이상, 4.x (같은 jar 하나로 지원) |

Spring 의존성은 대부분 `compileOnly` 이므로 서비스의 Spring Boot 버전을 따른다. 다만 Boot 3.4+ / Spring 6.2+ API
(`spring.http.client.*` 설정, `HandlerMethodValidationException.getParameterValidationResults` 등)를 사용하므로
**그보다 낮은 버전의 서비스에서는 런타임에 실패한다.**

테스트한 조합은 다음과 같다. Spring Cloud AWS 는 SQS 를 쓰는 서비스만 필요하다.

| Spring Boot | Spring Cloud AWS |
|---|---|
| 3.4.13 | 3.3.1 |
| 3.5.14 | 3.4.2 |
| 4.0.9 | 4.0.2 |
| 4.1.2 | 4.2.1 |

## 모듈

```
common-core                          순수 Java (예외 / 에러 코드 / 유틸)
 └─ common-spring-core               웹 무관 Spring (MDC)
     ├─ common-spring-web            웹 공통, 스택 무관 (응답 DTO, 예외 처리 기반, 접근 로그, 외부 호출, 검증)
     │   └─ common-spring-webmvc     Spring MVC (전역 예외 핸들러, /error, MDC / 접근 로그 필터)
     │       └─ common-spring-security-webmvc   MVC Security (401 / 403 JSON, JWT 필터, 블랙리스트 필터)
     └─ common-spring-security       Security 공통, 스택 무관 (JWT 발급 / 검증, 토큰 무효화 인터페이스)
         └─ common-spring-security-webmvc
common-jpa                           JPA 공통 (생성 / 수정 / 삭제 시각과 사용자 자동 기록, soft delete)
common-infra                         인프라 공통 (오브젝트 스토리지, Redis, 메시지 큐(SQS)). 인프라 SDK 는 서비스가 직접 추가

starter/common-*-starter             서비스가 추가하는 스타터. 위 모듈을 묶고 빈을 자동 설정으로 등록한다
```

모듈별 규칙과 클래스 책임은 각 모듈의 README 에, 사용 예시는 클래스 javadoc 에 있다.

## 서비스별로 추가할 스타터

스타터는 우리 모듈을 묶고 그 빈을 자동 설정으로 등록한다. 모듈만 추가하면 빈은 등록되지 않는다. Spring Boot starter 는 Boot 버전마다 이름이 달라질 수 있으므로 넣지 않고 서비스가 직접 추가한다.
상위 모듈이 하위 모듈을 `api` 로 포함하므로 스타터 하나가 필요한 하위 모듈을 모두 가져온다.

| 서비스 | 추가할 스타터 | 서비스에 있어야 하는 starter |
|---|---|---|
| Spring MVC API | `common-webmvc-starter` | `spring-boot-starter-web` (Boot 4 는 `spring-boot-starter-webmvc`) |
| Spring MVC API + Security / JWT | `common-security-webmvc-starter` | 위 starter, `spring-boot-starter-security` |
| 배치 / 컨슈머 (웹 없음) | 스타터 없이 `common-spring-core` 모듈 | - |
| JPA 사용 | 위 스타터 + `common-jpa-starter` | `spring-boot-starter-data-jpa` |
| 인프라 사용 | 위 스타터 + `common-infra-starter` | 쓰는 인프라의 SDK (예: AWS SDK S3, `spring-boot-starter-data-redis`, `spring-cloud-aws-starter-sqs`) |

```gradle
implementation 'com.iturtle:common-security-webmvc-starter:1.0-SNAPSHOT'
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'org.springframework.boot:spring-boot-starter-security'
```

선택 기능은 해당 라이브러리가 있을 때만 동작한다 (없어도 클래스 로딩이 깨지지 않는다).

| 기능 | 필요한 라이브러리 |
|---|---|
| `@Phone` / `@EnumValue`, `ConstraintViolationException` 400 처리 | `spring-boot-starter-validation` |
| `PageResponse.from(Page)` | Spring Data |
| 컨트롤러 안 Security 예외(`@PreAuthorize`) 401 / 403 처리 | Spring Security |
| 외부 호출 로그 | Boot 3 는 추가 없음, Boot 4 는 `spring-boot-starter-restclient` |

## 전체 규칙 요약

- **응답**: 모든 API 는 `ApiBaseResponse` 로 응답한다. 실패 응답은 전역 예외 핸들러만 만들고 컨트롤러는 예외를 던진다.
- **에러 코드**: `ErrorCode` 를 구현한 enum 으로 정의한다. 접두사 `C` 는 공통(`CommonErrorCode`), `J` 는 JWT(`JwtErrorCode`),
  도메인 코드는 서비스별 접두사를 쓴다. 응답 메시지는 `ErrorCode` 의 메시지이고, 예외의 상세 메시지는 로그에만 남는다.
- **예외 처리 확장**: `@RestControllerAdvice` 는 하나만 둔다. 잡을 예외를 추가하려면 `MvcGlobalExceptionHandler` 를 상속한다.
- **로그**: ECS 필드 이름을 쓴다. 로거는 `com.iturtle.error`(예외), `com.iturtle.access`(접근 로그),
  `com.iturtle.client`(외부 호출). 5xx 는 error, 4xx 는 warn, 404 는 debug. 응답과 로그에 입력값 / 비밀값을 그대로 남기지 않는다.
- **자동 설정**: 빈 등록은 스타터(`common-*-starter`)의 자동 설정만 한다. 모듈은 빈을 등록하지 않으므로 스타터 없이 모듈만 추가하면
  직접 빈으로 만들어 쓴다. 같은 타입 / 이름의 빈을 등록하면 대체된다. Security 필터처럼 조립이 필요한 것은 서비스가 직접 만든다.
- **선택 의존성**: `compileOnly` 로 두고, 해당 타입을 쓰는 코드는 분리해 라이브러리가 없어도 동작하게 한다.

## 빌드

```bash
./gradlew build                 # 컴파일 + 테스트
./gradlew javadoc               # javadoc 생성 (주석 오류 확인)
./gradlew test                  # 단위 + 통합 테스트
./gradlew publishToMavenLocal   # 로컬 Maven 저장소에 설치해 서비스에서 확인
```

자동 설정에 기대는 통합 테스트(`com.iturtle.integration.*`, `*AutoConfigurationTest`)는 스타터에 있다. 실제 Tomcat 을 띄우고,
라이브러리 패키지를 component scan 하지 않는 서비스와 같은 조건에서 자동 설정 / `/error` / Security 체인 조립을 검증한다.

### Spring Boot 호환성 테스트

main 은 지원 하한(`gradle.properties` 의 `springBootVersion`)으로 한 번만 컴파일하고, 테스트만 다른 Boot 에서 돌린다.
서비스가 받는 jar 와 같은 클래스를 검증하기 위해서다. Boot 또는 인프라 코드를 바꾸면 아래를 모두 돌린다.
컨테이너 테스트(`*ContainerTest`)는 Docker 가 없으면 건너뛰므로 Docker 를 켜고 돌린다.

```bash
./gradlew test                                # Boot 3.4 (기본)
./gradlew test -PtestBootVersion=3.5.14
./gradlew test -PtestBootVersion=4.0.9
./gradlew test -PtestBootVersion=4.1.2
```

Spring Cloud AWS 버전은 Boot 버전에 맞춰 선택되며(루트 `build.gradle`), `-PtestCloudAwsVersion` 으로 바꿀 수 있다.
Boot 4 에서는 Jackson 2 를 뺀 클래스패스로 도는 `testJackson3Only` 태스크가 함께 실행된다.

Boot 3 와 4 에서 패키지가 다른 타입을 쓰는 코드는 `common-webmvc-starter` 의 `src/boot4` 처럼 분리한다. 자동 설정의
`afterName` / `beforeName` 에 Boot 클래스 이름을 적을 때는 두 버전의 이름을 모두 적는다. 없는 이름은 오류 없이 무시되어
순서만 조용히 틀어진다.

## 라이선스

[Apache License 2.0](LICENSE)
