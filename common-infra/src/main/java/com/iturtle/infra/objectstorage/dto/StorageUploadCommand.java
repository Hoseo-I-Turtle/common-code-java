package com.iturtle.infra.objectstorage.dto;

import java.io.InputStream;

/**
 * 스토리지에 올릴 파일. content 는 호출한 쪽이 닫는다.
 *
 * @param storageKey       스토리지 안에서 파일을 가리킬 키 (예: posts/2026/10/uuid.png). 같은 키가 있으면 덮어쓴다
 * @param content          파일 내용
 * @param sizeBytes        파일 크기
 * @param contentType      MIME 타입 (예: image/png)
 * @param originalFilename 사용자가 올린 원본 파일 이름
 */
public record StorageUploadCommand(String storageKey, InputStream content, long sizeBytes, String contentType,
                                   String originalFilename) {
}
