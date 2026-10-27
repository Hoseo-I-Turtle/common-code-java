package com.iturtle.infra.objectstorage;

import com.iturtle.core.exception.BusinessException;
import com.iturtle.core.exception.CommonErrorCode;

/**
 * 스토리지 요청이 실패했다. 구현체는 SDK 예외를 이 예외로 감싸 던지므로, 호출한 쪽은 인프라와 무관하게 이것만 잡는다.
 * <p>
 * 외부 저장소 호출 실패이므로 CommonErrorCode.EXTERNAL_API_ERROR(502)로 응답한다. message 는 로그에만 남는다.
 */
public class StorageException extends BusinessException {

    public StorageException(String message, Throwable cause) {
        super(CommonErrorCode.EXTERNAL_API_ERROR, message, cause);
    }
}
