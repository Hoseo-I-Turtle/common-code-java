package com.iturtle.infra.objectstorage.dto;

import java.time.Instant;

/**
 * 스토리지에 있는 객체 정보 ({@code ObjectStorage#stat}). 클라이언트가 서명 주소로 직접 올린 뒤
 * 실제로 올라온 크기 · 형식을 확인할 때 쓴다 (클라이언트가 알려 준 값을 믿지 않는다).
 *
 * @param storageKey   객체 키
 * @param sizeBytes    크기
 * @param contentType  저장된 Content-Type (올린 쪽이 보낸 값이라 그대로 믿지 않는다). 없으면 null
 * @param etag         ETag (따옴표 없이)
 * @param lastModified 마지막 수정 시각
 */
public record StorageObjectInfo(String storageKey, long sizeBytes, String contentType, String etag, Instant lastModified) {
}
