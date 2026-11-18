package com.iturtle.spring.web.json;

import com.iturtle.spring.web.json.PatchOptionalModuleTest.PatchRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jackson 3 (Spring Boot 4). Boot 3 BOM 의 jackson-annotations 2.x 로는 Jackson 3 을 읽을 수 없어 Boot 4 로 테스트할 때만 돈다.
 */
class PatchOptionalJackson3ModuleTest {

    @Test
    void absentIsNull_nullIsEmpty_valueIsPresent() {
        JsonMapper mapper = JsonMapper.builder().addModule(new PatchOptionalJackson3Module()).build();

        PatchOptionalModuleTest.assertCases(json -> mapper.readValue(json, PatchRequest.class));
    }
}
