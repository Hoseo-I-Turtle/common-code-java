package com.iturtle.infra.objectstorage;

import com.iturtle.infra.objectstorage.dto.StorageUploadCommand;
import com.iturtle.infra.objectstorage.dto.StorageUploadResult;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;

/**
 * 오브젝트 스토리지(S3 등)에 파일을 올리고 받는다. 구현은 인프라별로 둔다.
 * <p>
 * key 는 스토리지 안에서 파일을 가리키는 키다 (StorageEntity 의 storageKey 와 같은 값, 예: posts/2026/10/uuid.png).
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
}
