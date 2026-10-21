package com.iturtle.spring.web.client;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpClientDefaultsEnvironmentPostProcessorTest {

    @Test
    void defaultsApplyOnlyWhenServiceDidNotSetThem() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(
                new MapPropertySource("application", Map.of("spring.http.client.read-timeout", "30s")));

        new HttpClientDefaultsEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());

        assertEquals("3s", environment.getProperty("spring.http.client.connect-timeout"));
        assertEquals("30s", environment.getProperty("spring.http.client.read-timeout"));
    }
}
