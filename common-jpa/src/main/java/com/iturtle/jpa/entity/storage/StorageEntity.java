package com.iturtle.jpa.entity.storage;

import com.iturtle.jpa.entity.CreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;

/**
 * 오브젝트 스토리지(S3 등)에 업로드한 파일의 기록. 파일 하나가 행 하나다.
 * <p>
 * 업로드 기록은 수정하지 않으므로 생성 시각에 더해 생성자만 기록한다 (AuditEntity 는 수정 기록까지 있어 상속하지 않는다.
 * 생성자 값은 AuditEntity 참고).
 * 대상과의 관계는 자식 엔티티가 직접 선언한다. 여러 종류의 대상에 붙는다면 PolymorphicStorageEntity 를 상속한다.
 * <pre>
 * &#64;Entity
 * public class ProfileImage extends StorageEntity {
 *     &#64;Id &#64;GeneratedValue
 *     private Long id;
 *
 *     &#64;ManyToOne(fetch = FetchType.LAZY)
 *     private User user;
 * }
 * </pre>
 */
@Getter
@MappedSuperclass
public abstract class StorageEntity extends CreatedAtEntity {

    /**
     * 스토리지 안에서 파일을 가리키는 키 (예: posts/2026/10/uuid.png).
     */
    @Column(nullable = false, updatable = false, unique = true)
    private String storageKey;

    @Column(updatable = false)
    private String originalFilename;

    @Column(updatable = false)
    private String contentType;

    @Column(nullable = false, updatable = false)
    private long sizeBytes;

    @CreatedBy
    @Column(updatable = false)
    private String createdBy;

    protected StorageEntity() {
    }

    protected StorageEntity(String storageKey, String originalFilename, String contentType, long sizeBytes) {
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
    }
}
