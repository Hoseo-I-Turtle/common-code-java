package com.iturtle.spring.web.logging;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * <pre>
 * iturtle:
 *   web:
 *     access-log:
 *       enabled: true
 *       exclude-paths: /actuator/**             # 지정하면 기본값을 대체
 *       additional-exclude-paths: /internal/**  # 기본값에 추가
 *       body-max-length: 2048
 *       masked-fields: password, token, ...     # 지정하면 기본값을 대체
 *       additional-masked-fields: email, phone  # 기본값에 추가
 * </pre>
 * List 프로퍼티는 바인딩 시 통째로 교체되므로, 기본값을 유지하면서 늘릴 때는 additional-* 를 사용한다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "iturtle.web.access-log")
public class AccessLogProperties {

    private boolean enabled = true;

    /** 접근 로그를 남기지 않을 경로 (Ant 패턴) */
    private List<String> excludePaths = new ArrayList<>(List.of("/actuator/**"));

    /** excludePaths 에 추가로 제외할 경로 */
    private List<String> additionalExcludePaths = new ArrayList<>();

    /** 에러 응답 시 남길 요청 본문 최대 바이트 */
    private int bodyMaxLength = 2048;

    /** 본문 / 쿼리스트링에서 값을 마스킹할 필드 이름 (대소문자 무시) */
    private List<String> maskedFields = new ArrayList<>(List.of(
            "password", "token", "accessToken", "refreshToken", "rrn", "cardNumber", "accountNumber"
    ));

    /** maskedFields 에 추가로 마스킹할 필드 이름 */
    private List<String> additionalMaskedFields = new ArrayList<>();
}
