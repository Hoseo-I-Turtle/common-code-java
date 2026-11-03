package com.iturtle.spring.core.boot;

import org.springframework.boot.SpringBootVersion;

/**
 * 서비스가 실행 중인 Spring Boot 의 메이저 버전을 알려준다. Boot 버전에 따라 동작을 나눠야 할 때 사용한다.
 * <p>
 * Spring Boot 가 있는 애플리케이션에서만 사용한다. Boot 버전에 따라 패키지가 다른 타입의 빈을 등록할 때는
 * 이 클래스 대신 자동 설정에 {@code @ConditionalOnClass(name = "...")} 를 쓴다. 그 타입을 참조하는 클래스는 해당 Boot 에서만
 * 로딩되어야 하기 때문이다.
 */
public final class BootVersion {

    private static final int MAJOR = parseMajor(SpringBootVersion.getVersion());

    private BootVersion() {
    }

    public static int major() {
        return MAJOR;
    }

    public static boolean isBoot3() {
        return MAJOR == 3;
    }

    public static boolean isBoot4() {
        return MAJOR == 4;
    }

    static int parseMajor(String version) {
        return Integer.parseInt(version.substring(0, version.indexOf('.')));
    }
}
