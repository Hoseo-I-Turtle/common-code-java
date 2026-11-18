package com.iturtle.infra.objectstorage.dto;

/**
 * 시작한 나눠 올리기 ({@code ObjectStorage#initiateMultipartUpload}). uploadId 로 조각 주소를 만들고 완료 · 취소한다.
 * 완료나 취소를 하지 않으면 올린 조각이 스토리지에 남으므로, 서비스는 오래된 세션을 취소하는 정리를 둔다
 * (또는 버킷 수명 주기 규칙 {@code AbortIncompleteMultipartUpload}).
 *
 * @param storageKey 객체 키
 * @param uploadId   스토리지가 준 세션 ID
 */
public record MultipartUploadSession(String storageKey, String uploadId) {
}
