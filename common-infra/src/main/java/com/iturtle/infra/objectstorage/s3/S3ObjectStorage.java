package com.iturtle.infra.objectstorage.s3;

import com.iturtle.infra.objectstorage.ObjectStorage;
import com.iturtle.infra.objectstorage.StorageException;
import com.iturtle.infra.objectstorage.dto.StorageUploadCommand;
import com.iturtle.infra.objectstorage.dto.StorageUploadResult;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;

/**
 * AWS S3 구현. 버킷 하나를 사용한다.
 */
public class S3ObjectStorage implements ObjectStorage {

    private final S3Client client;

    private final S3Presigner presigner;

    private final String bucket;

    public S3ObjectStorage(S3Client client, S3Presigner presigner, String bucket) {
        this.client = client;
        this.presigner = presigner;
        this.bucket = bucket;
    }

    @Override
    public StorageUploadResult upload(StorageUploadCommand command) {
        try {
            client.putObject(r -> r.bucket(bucket)
                            .key(command.storageKey())
                            .contentType(command.contentType())
                            .contentLength(command.sizeBytes()),
                    RequestBody.fromInputStream(command.content(), command.sizeBytes()));
        } catch (SdkException e) {
            throw new StorageException(command.storageKey() + " 업로드에 실패했다", e);
        }
        return new StorageUploadResult(command.storageKey(), command.originalFilename(), command.contentType(),
                command.sizeBytes());
    }

    @Override
    public InputStream download(String key) {
        try {
            return client.getObject(r -> r.bucket(bucket).key(key));
        } catch (SdkException e) {
            throw new StorageException(key + " 다운로드에 실패했다", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.deleteObject(r -> r.bucket(bucket).key(key));
        } catch (SdkException e) {
            throw new StorageException(key + " 삭제에 실패했다", e);
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            client.headObject(r -> r.bucket(bucket).key(key));
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw new StorageException(key + " 존재 확인에 실패했다", e);
        } catch (SdkException e) {
            throw new StorageException(key + " 존재 확인에 실패했다", e);
        }
    }

    @Override
    public URL createUploadUrl(String key, Duration expiresIn) {
        try {
            return presigner.presignPutObject(r -> r.signatureDuration(expiresIn)
                    .putObjectRequest(p -> p.bucket(bucket).key(key))).url();
        } catch (SdkException e) {
            throw new StorageException(key + " 업로드 URL 생성에 실패했다", e);
        }
    }

    @Override
    public URL createDownloadUrl(String key, Duration expiresIn) {
        try {
            return presigner.presignGetObject(r -> r.signatureDuration(expiresIn)
                    .getObjectRequest(g -> g.bucket(bucket).key(key))).url();
        } catch (SdkException e) {
            throw new StorageException(key + " 다운로드 URL 생성에 실패했다", e);
        }
    }
}
