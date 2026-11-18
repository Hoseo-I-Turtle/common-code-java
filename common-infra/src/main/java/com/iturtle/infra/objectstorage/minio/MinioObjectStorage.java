package com.iturtle.infra.objectstorage.minio;

import com.iturtle.infra.objectstorage.ObjectStorage;
import com.iturtle.infra.objectstorage.StorageException;
import com.iturtle.infra.objectstorage.dto.DownloadOptions;
import com.iturtle.infra.objectstorage.dto.StorageObjectInfo;
import com.iturtle.infra.objectstorage.dto.StorageUploadCommand;
import com.iturtle.infra.objectstorage.dto.StorageUploadResult;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import io.minio.http.Method;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 구현. 버킷 하나를 사용한다. MinIO SDK 는 checked 예외를 던지므로 StorageException 으로 감싼다.
 * <p>
 * 나눠 올리기(조각별 서명 주소)는 MinIO SDK 가 공개 API 로 제공하지 않아 지원하지 않는다. MinIO 서버에 나눠 올리기가
 * 필요하면 {@code type=s3} + {@code endpoint} + {@code path-style-access=true} 로 {@code S3ObjectStorage} 를 쓴다.
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
        return statObject(key, "존재 확인").isPresent();
    }

    @Override
    public URL createUploadUrl(String key, Duration expiresIn) {
        return presign(Method.PUT, key, expiresIn, Map.of(), "업로드");
    }

    @Override
    public URL createDownloadUrl(String key, Duration expiresIn) {
        return presign(Method.GET, key, expiresIn, Map.of(), "다운로드");
    }

    @Override
    public URL createDownloadUrl(String key, Duration expiresIn, DownloadOptions options) {
        Map<String, String> params = new HashMap<>();
        params.put("response-content-disposition", options.contentDispositionHeader());
        if (options.contentType() != null) {
            params.put("response-content-type", options.contentType());
        }
        return presign(Method.GET, key, expiresIn, params, "다운로드");
    }

    @Override
    public Optional<StorageObjectInfo> stat(String key) {
        return statObject(key, "정보 조회").map(stat -> new StorageObjectInfo(key, stat.size(), stat.contentType(),
                stat.etag(), stat.lastModified() == null ? null : stat.lastModified().toInstant()));
    }

    @Override
    public InputStream downloadRange(String key, long offset, long length) {
        if (offset < 0 || length < 1) {
            throw new IllegalArgumentException("offset 은 0 이상, length 는 1 이상이어야 한다");
        }
        try {
            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).offset(offset).length(length).build());
        } catch (MinioException | IOException | GeneralSecurityException e) {
            throw new StorageException(key + " 범위 다운로드에 실패했다", e);
        }
    }

    private Optional<StatObjectResponse> statObject(String key, String action) {
        try {
            return Optional.of(client.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build()));
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return Optional.empty();
            }
            throw new StorageException(key + " " + action + "에 실패했다", e);
        } catch (MinioException | IOException | GeneralSecurityException e) {
            throw new StorageException(key + " " + action + "에 실패했다", e);
        }
    }

    private URL presign(Method method, String key, Duration expiresIn, Map<String, String> extraQueryParams, String action) {
        try {
            String url = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(method)
                    .bucket(bucket)
                    .object(key)
                    .expiry((int) expiresIn.toSeconds(), TimeUnit.SECONDS)
                    .extraQueryParams(extraQueryParams)
                    .build());
            return URI.create(url).toURL();
        } catch (MinioException | IOException | GeneralSecurityException e) {
            throw new StorageException(key + " " + action + " URL 생성에 실패했다", e);
        }
    }
}
