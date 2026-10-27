package com.iturtle.infra.objectstorage.minio;

import com.iturtle.infra.objectstorage.ObjectStorage;
import com.iturtle.infra.objectstorage.StorageException;
import com.iturtle.infra.objectstorage.dto.StorageUploadCommand;
import com.iturtle.infra.objectstorage.dto.StorageUploadResult;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import io.minio.http.Method;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 구현. 버킷 하나를 사용한다. MinIO SDK 는 checked 예외를 던지므로 StorageException 으로 감싼다.
 */
public class MinioObjectStorage implements ObjectStorage {

    private final MinioClient client;

    private final String bucket;

    public MinioObjectStorage(MinioClient client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public StorageUploadResult upload(StorageUploadCommand command) {
        PutObjectArgs.Builder args = PutObjectArgs.builder()
                .bucket(bucket)
                .object(command.storageKey())
                .stream(command.content(), command.sizeBytes(), -1);
        if (command.contentType() != null) {
            args.contentType(command.contentType());
        }
        try {
            client.putObject(args.build());
        } catch (MinioException | IOException | GeneralSecurityException e) {
            throw new StorageException(command.storageKey() + " 업로드에 실패했다", e);
        }
        return new StorageUploadResult(command.storageKey(), command.originalFilename(), command.contentType(),
                command.sizeBytes());
    }

    @Override
    public InputStream download(String key) {
        try {
            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (MinioException | IOException | GeneralSecurityException e) {
            throw new StorageException(key + " 다운로드에 실패했다", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (MinioException | IOException | GeneralSecurityException e) {
            throw new StorageException(key + " 삭제에 실패했다", e);
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            client.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
            return true;
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return false;
            }
            throw new StorageException(key + " 존재 확인에 실패했다", e);
        } catch (MinioException | IOException | GeneralSecurityException e) {
            throw new StorageException(key + " 존재 확인에 실패했다", e);
        }
    }

    @Override
    public URL createUploadUrl(String key, Duration expiresIn) {
        return presign(Method.PUT, key, expiresIn, "업로드");
    }

    @Override
    public URL createDownloadUrl(String key, Duration expiresIn) {
        return presign(Method.GET, key, expiresIn, "다운로드");
    }

    private URL presign(Method method, String key, Duration expiresIn, String action) {
        try {
            String url = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(method)
                    .bucket(bucket)
                    .object(key)
                    .expiry((int) expiresIn.toSeconds(), TimeUnit.SECONDS)
                    .build());
            return URI.create(url).toURL();
        } catch (MinioException | IOException | GeneralSecurityException e) {
            throw new StorageException(key + " " + action + " URL 생성에 실패했다", e);
        }
    }
}
