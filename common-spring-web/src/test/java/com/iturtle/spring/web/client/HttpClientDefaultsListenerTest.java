package com.iturtle.spring.web.client;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpClientDefaultsListenerTest {

    @Test
    void defaultsApplyOnlyWhenServiceDidNotSetThem() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("application", Map.of(
                "spring.http.client.read-timeout", "30s",
                "spring.http.clients.read-timeout", "40s")));

        HttpClientDefaultsListener.applyDefaults(environment);

        assertEquals("3s", environment.getProperty("spring.http.client.connect-timeout"));
        assertEquals("30s", environment.getProperty("spring.http.client.read-timeout"));
        assertEquals("3s", environment.getProperty("spring.http.clients.connect-timeout"));
        assertEquals("40s", environment.getProperty("spring.http.clients.read-timeout"));
    }
}
