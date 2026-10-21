package com.iturtle.spring.web.dto.response;

import com.iturtle.core.exception.ErrorCode;

/**
 * 공통 응답 형식. 성공이면 data, 실패면 error 가 채워진다.
 * <pre>
 * return ApiBaseResponse.success(orderResponse);
 *
 * { "success": true,  "data": { ... }, "error": null }
 * { "success": false, "data": null,
 *   "error": { "status": 400, "code": "C001", "message": "잘못된 입력값입니다.",
 *              "errors": [ { "field": "email", "reason": "must not be blank" } ],
 *              "traceId": "6f1c..." } }
 * </pre>
 * 실패 응답은 전역 예외 핸들러가 만들므로 컨트롤러에서는 예외를 던진다.
 */
public record ApiBaseResponse<T>(
        boolean success,
        T data,
        ErrorResponse error
) {

    public static <T> ApiBaseResponse<T> success(T data) {
        return new ApiBaseResponse<>(true, data, null);
    }

    public static <T> ApiBaseResponse<T> fail(ErrorCode errorCode) {
        return fail(new ErrorResponse(errorCode));
    }

    public static <T> ApiBaseResponse<T> fail(ErrorResponse error) {
        return new ApiBaseResponse<>(false, null, error);
    }

}
