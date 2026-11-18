package com.iturtle.jpa.entity.job;

/**
 * {@link RetryableJobEntity} 의 상태. {@code PENDING} → {@code RUNNING} → {@code SUCCEEDED},
 * 실패하면 {@code PENDING}(다음 시도 대기) 으로 돌아가고 시도 횟수를 다 쓰면 {@code FAILED}.
 */
public enum RetryableJobStatus {
    PENDING, RUNNING, SUCCEEDED, FAILED
}
