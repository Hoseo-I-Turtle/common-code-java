package com.iturtle.infra.redis;

import com.iturtle.core.exception.BusinessException;
import com.iturtle.core.exception.CommonErrorCode;

/**
 * Redis 가 응답하지 않았다. RedisStore 의 OrThrow 메서드만 던진다. 부르는 쪽이 장애를 알고 다른 길(DB 잠금 등)로
 * 가야 하는 자리에서만 쓴다.
 * <p>
 * 외부 저장소 호출 실패이므로 CommonErrorCode.EXTERNAL_API_ERROR(502)로 응답한다. message 는 로그에만 남는다.
 */
public class RedisUnavailableException extends BusinessException {

    public RedisUnavailableException(String message, Throwable cause) {
        super(CommonErrorCode.EXTERNAL_API_ERROR, message, cause);
    }
}
