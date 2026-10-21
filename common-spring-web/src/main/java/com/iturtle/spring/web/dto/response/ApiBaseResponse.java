package com.iturtle.spring.web.dto.response;

import com.iturtle.core.exception.ErrorCode;

public record ApiBaseResponse<T>(
        boolean success,
        T data,
        ErrorResponse error
) {

    public static <T> ApiBaseResponse<T> success(T data) {
        return new ApiBaseResponse<>(true, data, null);
    }

    public static <T> ApiBaseResponse<T> fail(ErrorCode errorCode) {
        return new ApiBaseResponse<>(false, null, new ErrorResponse(errorCode));
    }

}
