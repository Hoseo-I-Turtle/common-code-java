package com.iturtle.jpa.entity.storage;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 여러 종류의 대상에 붙는 업로드 파일을 한 테이블에 기록한다 (polymorphic association).
 * <p>
 * 대상은 FK 가 아니라 종류(targetType) + 식별자(targetId)로 가리킨다. TARGET_TYPE 은 enum 이나 String 이며, 어느 쪽이든 문자열로
 * 저장한다 (enum 은 이름). FK 가 없으므로 대상이 삭제되어도 DB 가 막지 않는다. 남은 행은 서비스가 정리한다.
 * <pre>
 * public enum FileTarget { POST, COMMENT }
 *
 * &#64;Entity
 * &#64;Table(indexes = @Index(columnList = "target_type, target_id"))
 * public class Attachment extends PolymorphicStorageEntity&lt;FileTarget&gt; {
 *     &#64;Id &#64;GeneratedValue
 *     private Long id;
 * }
 * </pre>
 * 인덱스는 상위 클래스(@MappedSuperclass)에 둘 수 없으므로 자식 엔티티가 선언한다.
 */
@Getter
@MappedSuperclass
public abstract class PolymorphicStorageEntity<TARGET_TYPE> extends StorageEntity {

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, updatable = false)
    private TARGET_TYPE targetType;

    /**
     * 대상의 식별자. 숫자 / UUID 등 형식이 정해져 있지 않으므로 문자열로 둔다.
     */
    @Column(nullable = false, updatable = false)
    private String targetId;

    protected PolymorphicStorageEntity() {
    }

    protected PolymorphicStorageEntity(String storageKey, String originalFilename, String contentType, long sizeBytes,
                                       TARGET_TYPE targetType, String targetId) {
        super(storageKey, originalFilename, contentType, sizeBytes);
        this.targetType = targetType;
        this.targetId = targetId;
    }
}
