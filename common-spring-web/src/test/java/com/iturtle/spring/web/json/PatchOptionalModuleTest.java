package com.iturtle.spring.web.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Jackson 2 (Spring Boot 3). Jackson 3 은 {@link PatchOptionalJackson3ModuleTest}.
 */
class PatchOptionalModuleTest {

    public record PatchRequest(Optional<String> title, Optional<LocalDate> pinnedUntilOn, Optional<List<Long>> fileIds,
                        String plain) {
    }

    @Test
    void jackson2_afterJdk8Module_absentIsNull_nullIsEmpty_valueIsPresent() throws Exception {
        // Spring Boot 3 처럼 Jdk8Module(잘 알려진 모듈)을 먼저, 빈으로 등록한 모듈을 나중에 넣는다
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new Jdk8Module())
                .registerModule(new JavaTimeModule())
                .registerModule(new PatchOptionalJackson2Module());

        assertCases(json -> {
            try {
                return mapper.readValue(json, PatchRequest.class);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    static void assertCases(java.util.function.Function<String, PatchRequest> read) {
        PatchRequest absent = read.apply("{}");
        assertNull(absent.title());
        assertNull(absent.pinnedUntilOn());
        assertNull(absent.fileIds());

        PatchRequest nulls = read.apply("{\"title\": null, \"pinnedUntilOn\": null, \"fileIds\": null}");
        assertEquals(Optional.empty(), nulls.title());
        assertEquals(Optional.empty(), nulls.pinnedUntilOn());
        assertEquals(Optional.empty(), nulls.fileIds());

        PatchRequest values = read.apply("{\"title\": \"공지\", \"pinnedUntilOn\": \"2026-11-30\", \"fileIds\": [1, 2]}");
        assertEquals(Optional.of("공지"), values.title());
        assertEquals(Optional.of(LocalDate.of(2026, 11, 30)), values.pinnedUntilOn());
        assertEquals(Optional.of(List.of(1L, 2L)), values.fileIds());
    }
}
