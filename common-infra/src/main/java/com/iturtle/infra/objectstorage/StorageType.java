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
     * MinIO (MinioObjectStorage, MinIO SDK 필요).
     */
    MINIO
}
