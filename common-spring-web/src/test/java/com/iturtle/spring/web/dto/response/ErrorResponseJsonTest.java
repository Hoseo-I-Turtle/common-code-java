package com.iturtle.spring.web.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iturtle.core.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ErrorResponseJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void absentErrorsAndTraceIdOmitted() throws Exception {
        JsonNode json = objectMapper.valueToTree(ApiBaseResponse.fail(CommonErrorCode.NOT_FOUND));

        // 성공 / 실패 응답의 모양을 맞추기 위해 ApiBaseResponse 의 data 는 null 이어도 남긴다
        assertTrue(json.has("data"));
        JsonNode error = json.get("error");
        assertEquals("C004", error.get("code").asText());
        assertFalse(error.has("errors"));
        assertFalse(error.has("traceId"));
    }

    @Test
    void presentErrorsAndTraceIdIncluded() {
        ErrorResponse error = new ErrorResponse(CommonErrorCode.INVALID_INPUT,
                List.of(new FieldErrorResponse("email", "must not be blank")), "trace-1");

        JsonNode json = objectMapper.valueToTree(error);

        assertEquals("email", json.get("errors").get(0).get("field").asText());
        assertEquals("trace-1", json.get("traceId").asText());
    }
}
