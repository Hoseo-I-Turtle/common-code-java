package com.iturtle.infra.objectstorage.dto;

/**
 * 올린 조각 하나. 클라이언트가 조각 주소로 PUT 한 응답의 {@code ETag} 헤더를 그대로 넘긴다
 * (브라우저가 읽으려면 버킷 CORS 의 ExposeHeaders 에 ETag 가 있어야 한다).
 *
 * @param partNumber 1 ~ 10000
 * @param etag       조각의 ETag (따옴표가 있어도 됨)
 */
public record CompletedPart(int partNumber, String etag) {

    public CompletedPart {
        if (partNumber < 1 || partNumber > 10_000) {
            throw new IllegalArgumentException("partNumber 는 1 ~ 10000 이어야 한다: " + partNumber);
        }
        if (etag == null || etag.isBlank()) {
            throw new IllegalArgumentException("etag 가 비어 있다 (partNumber " + partNumber + ")");
        }
    }
}
