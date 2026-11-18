package com.iturtle.infra.objectstorage;

import com.iturtle.infra.objectstorage.dto.CompletedPart;
import com.iturtle.infra.objectstorage.dto.DownloadOptions;
import com.iturtle.infra.objectstorage.dto.MultipartUploadSession;
import com.iturtle.infra.objectstorage.dto.StorageObjectInfo;
import com.iturtle.infra.objectstorage.dto.StorageUploadCommand;
import com.iturtle.infra.objectstorage.dto.StorageUploadResult;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 오브젝트 스토리지(S3 등)에 파일을 올리고 받는다. 구현은 인프라별로 둔다.
 * <p>
 * key 는 스토리지 안에서 파일을 가리키는 키다 (StorageEntity 의 storageKey 와 같은 값, 예: posts/2026/10/uuid.png).
 * <p>
 * 큰 파일을 브라우저가 서버를 거치지 않고 올리는 흐름 (나눠 올리기):
 * <pre>
 * MultipartUploadSession session = storage.initiateMultipartUpload(key, null);
 * List&lt;URL&gt; urls = IntStream.rangeClosed(1, partCount)
 *         .mapToObj(n -&gt; storage.createUploadPartUrl(key, session.uploadId(), n, Duration.ofHours(1))).toList();
 * // 브라우저가 조각마다 PUT 하고 응답 ETag 를 모아 돌려준다 (마지막 조각 외에는 5MB 이상)
 * storage.completeMultipartUpload(key, session.uploadId(), parts);
 * StorageObjectInfo info = storage.stat(key).orElseThrow();       // 실제 크기 확인
 * byte[] head = storage.downloadRange(key, 0, 512).readAllBytes(); // 실제 형식 확인 (FileTypePolicy)
 * </pre>
 * 새로 더한 메서드(stat 이후)는 기본 구현이 {@link UnsupportedOperationException} 을 던진다. 직접 구현체를 둔 서비스가
 * 버전을 올려도 컴파일이 깨지지 않게 하기 위해서다. 라이브러리의 S3 · MinIO 구현은 모두 구현한다 (MinIO 의 나눠 올리기 제외).
 */
public interface ObjectStorage {

    /**
     * 파일을 올리고 올린 파일의 정보를 돌려준다.
     */
    StorageUploadResult upload(StorageUploadCommand command);

    /**
     * 파일을 받는다. 반환한 스트림은 호출한 쪽이 닫는다.
     */
    InputStream download(String key);

    /**
     * 파일을 지운다. 없는 key 여도 실패하지 않는다.
     */
    void delete(String key);

    boolean exists(String key);

    /**
     * 클라이언트가 서버를 거치지 않고 직접 올릴 수 있는 URL 을 만든다. expiresIn 이 지나면 쓸 수 없다.
     */
    URL createUploadUrl(String key, Duration expiresIn);

    /**
     * 클라이언트가 서버를 거치지 않고 직접 받을 수 있는 URL 을 만든다. expiresIn 이 지나면 쓸 수 없다.
     */
    URL createDownloadUrl(String key, Duration expiresIn);

    /**
     * 받을 파일 이름 · 열기 방식(inline / attachment) · Content-Type 을 정한 내려받기 URL.
     * 권한을 확인한 뒤 짧게(예: 5분) 만들어 준다. 브라우저에서 열 수 있는 형식(이미지 · PDF) 외에는 attachment 로 만든다.
     */
    default URL createDownloadUrl(String key, Duration expiresIn, DownloadOptions options) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " 는 내려받기 옵션을 지원하지 않는다");
    }

    /**
     * 객체 정보. 없으면 비어 있다.
     */
    default Optional<StorageObjectInfo> stat(String key) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " 는 stat 을 지원하지 않는다");
    }

    /**
     * offset 부터 length 바이트를 받는다 (파일 앞부분으로 형식을 확인할 때). 파일이 더 짧으면 있는 만큼만 온다.
     * 반환한 스트림은 호출한 쪽이 닫는다.
     */
    default InputStream downloadRange(String key, long offset, long length) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " 는 범위 받기를 지원하지 않는다");
    }

    /**
     * 나눠 올리기({@link #initiateMultipartUpload} 등)를 지원하는지.
     */
    default boolean supportsMultipartUpload() {
        return false;
    }

    /**
     * 나눠 올리기를 시작한다.
     *
     * @param contentType 저장할 Content-Type. null 이면 정하지 않는다
     */
    default MultipartUploadSession initiateMultipartUpload(String key, String contentType) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " 는 나눠 올리기를 지원하지 않는다");
    }

    /**
     * 조각 하나를 클라이언트가 직접 올릴 PUT URL.
     */
    default URL createUploadPartUrl(String key, String uploadId, int partNumber, Duration expiresIn) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " 는 나눠 올리기를 지원하지 않는다");
    }

    /**
     * 올린 조각을 합쳐 객체 하나로 만든다. parts 는 partNumber 순서로 정렬해 보낸다.
     */
    default void completeMultipartUpload(String key, String uploadId, List<CompletedPart> parts) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " 는 나눠 올리기를 지원하지 않는다");
    }

    /**
     * 나눠 올리기를 취소하고 올린 조각을 지운다. 이미 끝났거나 없는 세션이어도 실패하지 않는다.
     */
    default void abortMultipartUpload(String key, String uploadId) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " 는 나눠 올리기를 지원하지 않는다");
    }
}
