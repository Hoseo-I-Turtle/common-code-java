package com.iturtle.infra.objectstorage.s3;

import com.iturtle.infra.objectstorage.ObjectStorage;
import com.iturtle.infra.objectstorage.StorageException;
import com.iturtle.infra.objectstorage.dto.CompletedPart;
import com.iturtle.infra.objectstorage.dto.DownloadOptions;
import com.iturtle.infra.objectstorage.dto.MultipartUploadSession;
import com.iturtle.infra.objectstorage.dto.StorageObjectInfo;
import com.iturtle.infra.objectstorage.dto.StorageUploadCommand;
import com.iturtle.infra.objectstorage.dto.StorageUploadResult;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchUploadException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * AWS S3 구현. 버킷 하나를 사용한다. S3 호환 저장소(MinIO, Ceph 등)도 {@code endpoint} + path-style 로 이 구현을 쓴다.
 * 나눠 올리기(조각별 서명 주소)는 이 구현만 지원한다.
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
        return head(key, "존재 확인").isPresent();
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

    @Override
    public URL createDownloadUrl(String key, Duration expiresIn, DownloadOptions options) {
        try {
            return presigner.presignGetObject(r -> r.signatureDuration(expiresIn)
                    .getObjectRequest(g -> g.bucket(bucket).key(key)
                            .responseContentDisposition(options.contentDispositionHeader())
                            .responseContentType(options.contentType()))).url();
        } catch (SdkException e) {
            throw new StorageException(key + " 다운로드 URL 생성에 실패했다", e);
        }
    }

    @Override
    public Optional<StorageObjectInfo> stat(String key) {
        return head(key, "정보 조회").map(head -> new StorageObjectInfo(key, head.contentLength(), head.contentType(),
                unquote(head.eTag()), head.lastModified()));
    }

    @Override
    public InputStream downloadRange(String key, long offset, long length) {
        requireRange(offset, length);
        try {
            return client.getObject(r -> r.bucket(bucket).key(key).range("bytes=" + offset + "-" + (offset + length - 1)));
        } catch (SdkException e) {
            throw new StorageException(key + " 범위 다운로드에 실패했다", e);
        }
    }

    @Override
    public boolean supportsMultipartUpload() {
        return true;
    }

    @Override
    public MultipartUploadSession initiateMultipartUpload(String key, String contentType) {
        try {
            String uploadId = client.createMultipartUpload(r -> r.bucket(bucket).key(key).contentType(contentType))
                    .uploadId();
            return new MultipartUploadSession(key, uploadId);
        } catch (SdkException e) {
            throw new StorageException(key + " 나눠 올리기 시작에 실패했다", e);
        }
    }

    @Override
    public URL createUploadPartUrl(String key, String uploadId, int partNumber, Duration expiresIn) {
        if (partNumber < 1 || partNumber > 10_000) {
            throw new IllegalArgumentException("partNumber 는 1 ~ 10000 이어야 한다: " + partNumber);
        }
        try {
            return presigner.presignUploadPart(r -> r.signatureDuration(expiresIn)
                    .uploadPartRequest(u -> u.bucket(bucket).key(key).uploadId(uploadId).partNumber(partNumber))).url();
        } catch (SdkException e) {
            throw new StorageException(key + " 조각 업로드 URL 생성에 실패했다", e);
        }
    }

    @Override
    public void completeMultipartUpload(String key, String uploadId, List<CompletedPart> parts) {
        if (parts == null || parts.isEmpty()) {
            throw new IllegalArgumentException("조각이 하나 이상 있어야 한다");
        }
        List<software.amazon.awssdk.services.s3.model.CompletedPart> completed = parts.stream()
                .sorted(Comparator.comparingInt(CompletedPart::partNumber))
                .map(part -> software.amazon.awssdk.services.s3.model.CompletedPart.builder()
                        .partNumber(part.partNumber())
                        .eTag(quote(part.etag()))
                        .build())
                .toList();
        try {
            client.completeMultipartUpload(r -> r.bucket(bucket).key(key).uploadId(uploadId)
                    .multipartUpload(m -> m.parts(completed)));
        } catch (SdkException e) {
            throw new StorageException(key + " 나눠 올리기 완료에 실패했다", e);
        }
    }

    @Override
    public void abortMultipartUpload(String key, String uploadId) {
        try {
            client.abortMultipartUpload(r -> r.bucket(bucket).key(key).uploadId(uploadId));
        } catch (NoSuchUploadException e) {
            // 이미 끝났거나 없는 세션
        } catch (S3Exception e) {
            if (e.statusCode() != 404) {
                throw new StorageException(key + " 나눠 올리기 취소에 실패했다", e);
            }
        } catch (SdkException e) {
            throw new StorageException(key + " 나눠 올리기 취소에 실패했다", e);
        }
    }

    private Optional<HeadObjectResponse> head(String key, String action) {
        try {
            return Optional.of(client.headObject(r -> r.bucket(bucket).key(key)));
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw new StorageException(key + " " + action + "에 실패했다", e);
        } catch (SdkException e) {
            throw new StorageException(key + " " + action + "에 실패했다", e);
        }
    }

    static void requireRange(long offset, long length) {
        if (offset < 0 || length < 1) {
            throw new IllegalArgumentException("offset 은 0 이상, length 는 1 이상이어야 한다");
        }
    }

    private static String unquote(String etag) {
        if (etag != null && etag.length() >= 2 && etag.startsWith("\"") && etag.endsWith("\"")) {
            return etag.substring(1, etag.length() - 1);
        }
        return etag;
    }

    private static String quote(String etag) {
        String value = etag.strip();
        return value.startsWith("\"") ? value : "\"" + value + "\"";
    }
}
