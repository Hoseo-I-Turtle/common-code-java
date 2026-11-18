package com.iturtle.infra.objectstorage;

/**
 * ObjectStorage 구현체 종류 (iturtle.infra.storage.type).
 */
public enum StorageType {

    /**
     * AWS S3 (S3ObjectStorage, AWS SDK 필요).
     */
    S3,

    /**
     * MinIO (MinioObjectStorage, MinIO SDK 필요). 나눠 올리기를 지원하지 않는다.
     * <p>
     * 새로 쓰지 않는다: MinIO 커뮤니티판은 2025-10 부터 Docker 이미지를 배포하지 않는다. 이미 MinIO 서버가 있으면
     * {@link #S3} + endpoint + path-style 로 붙는 편이 나눠 올리기까지 된다. 이미 쓰는 서비스를 위해 남겨 둔다.
     */
    MINIO,

    /**
     * Garage (S3 호환 자체 호스팅 저장소). S3ObjectStorage 를 쓰고 AWS SDK 가 필요하다.
     * endpoint · access-key · secret-key 가 필수이고, path-style 주소와 "체크섬은 필요할 때만"을 자동으로 맞춘다.
     * region 을 비우면 Garage 기본값 {@code garage} (garage.toml 의 s3_region 과 같아야 서명이 맞는다).
     */
    GARAGE
}
