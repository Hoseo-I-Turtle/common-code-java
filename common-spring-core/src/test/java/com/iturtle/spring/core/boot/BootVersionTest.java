package com.iturtle.spring.core.boot;

import org.junit.jupiter.api.Test;
import org.springframework.util.ClassUtils;

import static org.junit.jupiter.api.Assertions.*;

class BootVersionTest {

    @Test
    void parsesMajorVersion() {
        assertEquals(3, BootVersion.parseMajor("3.4.13"));
        assertEquals(4, BootVersion.parseMajor("4.1.2"));
        assertEquals(4, BootVersion.parseMajor("4.0.0-RC1"));
    }

    @Test
    void matchesRunningBoot() {
        // 이 클래스는 Boot 4 의 spring-boot jar 에만 있다
        boolean boot4Class = ClassUtils.isPresent("org.springframework.boot.EnvironmentPostProcessor", null);

        assertEquals(boot4Class, BootVersion.isBoot4());
        assertEquals(!boot4Class, BootVersion.isBoot3());
        assertEquals(boot4Class ? 4 : 3, BootVersion.major());
    }
}
