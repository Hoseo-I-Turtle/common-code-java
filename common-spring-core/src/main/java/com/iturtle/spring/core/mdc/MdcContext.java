package com.iturtle.spring.core.mdc;

import org.slf4j.MDC;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MDC 에 넣을 값 묶음. 기본값을 받고, 사용처에서 put 으로 추가한 뒤 open 으로 MDC 에 반영한다.
 * <pre>
 * try (MdcContext ignored = new MdcContext(defaults).put("user.id", userId).open()) {
 *     ... // 이 구간 로그에 반영, 벗어나면 이전 값으로 복원
 * }
 * </pre>
 */
public class MdcContext implements AutoCloseable {

    private final Map<String, String> entries = new LinkedHashMap<>();
    private final Map<String, String> previous = new HashMap<>();

    public MdcContext() {}

    public MdcContext(Map<String, String> defaults) {
        putAll(defaults);
    }

    /**
     * key 가 비어 있거나 value 가 null 이면 무시한다. 같은 key 는 나중 값으로 덮어쓴다.
     */
    public MdcContext put(String key, String value) {
        if (key == null || key.isBlank() || value == null) {
            return this;
        }
        entries.put(key, value);
        return this;
    }

    public MdcContext putAll(Map<String, String> values) {
        if (values != null) {
            values.forEach(this::put);
        }
        return this;
    }

    public Map<String, String> getEntries() {
        return Collections.unmodifiableMap(entries);
    }

    /**
     * 현재 스레드 MDC 에 반영. close 하면 반영한 key 만 이전 값으로 복원한다.
     */
    public MdcContext open() {
        entries.forEach((key, value) -> {
            previous.put(key, MDC.get(key));
            MDC.put(key, value);
        });
        return this;
    }

    @Override
    public void close() {
        previous.forEach((key, value) -> {
            if (value == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, value);
            }
        });
        previous.clear();
    }
}
