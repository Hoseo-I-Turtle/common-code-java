package com.iturtle.jpa.entity.job;

import com.iturtle.jpa.entity.CreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * 큐 등에서 받은 작업이 여러 단계를 거칠 때 진행 상황과 시작 / 종료 시각을 기록한다.
 * <p>
 * JOB_STATUS 는 enum 이나 String 이며, 어느 쪽이든 문자열로 저장한다 (enum 은 이름). 단계 구성, 실패 / 재시도,
 * 작업 인자는 자식 엔티티가 정한다. 외부 작업(업로드 등)은 DB 트랜잭션에 묶이지 않으므로 재시도 시 같은 단계가
 * 다시 실행될 수 있다. 각 단계는 다시 실행해도 안전하게 만든다.
 * <pre>
 * public enum ResizeStatus { RECEIVED, DOWNLOADING, RESIZING, UPLOADING, DONE }
 *
 * &#64;Entity
 * public class ResizeJob extends JobEntity&lt;ResizeStatus&gt; {
 *     &#64;Id &#64;GeneratedValue
 *     private Long id;
 * }
 *
 * job.markRunning(DOWNLOADING);
 * job.markRunning(RESIZING);
 * job.markFinished(DONE);
 * </pre>
 */
@Getter
@MappedSuperclass
public abstract class JobEntity<JOB_STATUS> extends CreatedAtEntity {

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private JOB_STATUS status;

    private Instant startedAt;

    private Instant finishedAt;

    protected JobEntity() {
    }

    protected JobEntity(JOB_STATUS status) {
        this.status = status;
    }

    /**
     * 진행 중인 단계로 바꾼다. 처음 진행할 때 시작 시각을 기록한다.
     */
    public void markRunning(JOB_STATUS status) {
        this.status = status;
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }

    /**
     * 끝난 상태로 바꾸고 종료 시각을 기록한다.
     */
    public void markFinished(JOB_STATUS status) {
        this.status = status;
        finishedAt = Instant.now();
    }

}
