package com.iturtle.spring.web.client;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ClientLogInterceptorTest {

    private final ClientLogInterceptor interceptor = new ClientLogInterceptor();
    private final Logger clientLogger = (Logger) LoggerFactory.getLogger("com.iturtle.client");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final MockClientHttpRequest request =
            new MockClientHttpRequest(HttpMethod.GET, URI.create("https://api.example.com/orders?token=secret"));

    @BeforeEach
    void setUp() {
        appender.start();
        clientLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        clientLogger.detachAppender(appender);
    }

    @Test
    void logsWithoutQueryString() throws IOException {
        interceptor.intercept(request, new byte[0], (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK));

        ILoggingEvent event = appender.list.get(0);
        assertEquals(Level.INFO, event.getLevel());
        assertEquals("https://api.example.com/orders", fields(event).get("url.full"));
        assertEquals(200, fields(event).get("http.response.status_code"));
    }

    @Test
    void errorStatusLoggedAsWarn() throws IOException {
        interceptor.intercept(request, new byte[0],
                (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.SERVICE_UNAVAILABLE));

        assertEquals(Level.WARN, appender.list.get(0).getLevel());
    }

    @Test
    void ioExceptionLoggedAndRethrown() {
        assertThrows(SocketTimeoutException.class, () -> interceptor.intercept(request, new byte[0], (req, body) -> {
            throw new SocketTimeoutException("Read timed out");
        }));

        ILoggingEvent event = appender.list.get(0);
        assertEquals(Level.WARN, event.getLevel());
        assertEquals(SocketTimeoutException.class.getName(), fields(event).get("error.type"));
        assertFalse(fields(event).containsKey("http.response.status_code"));
    }

    private Map<String, Object> fields(ILoggingEvent event) {
        Map<String, Object> fields = new HashMap<>();
        for (KeyValuePair pair : event.getKeyValuePairs()) {
            fields.put(pair.key, pair.value);
        }
        return fields;
    }
}
