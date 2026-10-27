package com.iturtle.infra.objectstorage.dto;

/**
 * 스토리지에 올린 파일의 정보. 필드는 StorageEntity(common-jpa)와 같으므로 업로드 기록을 저장할 때 그대로 옮긴다.
 *
 * @param storageKey       스토리지 안에서 파일을 가리키는 키
 * @param originalFilename 사용자가 올린 원본 파일 이름
 * @param contentType      MIME 타입 (예: image/png)
 * @param sizeBytes        파일 크기
 */
public record StorageUploadResult(String storageKey, String originalFilename, String contentType, long sizeBytes) {
}
