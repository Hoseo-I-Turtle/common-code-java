package com.iturtle.jpa.entity.job;

import com.iturtle.core.retry.BackoffPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

import java.time.Instant;
import java.util.Optional;

/**
 * 실패하면 간격을 두고 다시 시도하는 작업. 외부 서비스(Vault · Jenkins · GitHub 등) 호출처럼 DB 트랜잭션에 묶을 수 없는 일을
 * <b>도메인 변경과 같은 트랜잭션에서 이 행으로 남기고</b>, 주기 작업이 꺼내 실행한다 (transactional outbox 와 같은 보장:
 * 커밋되면 반드시 실행되고, 롤백되면 실행되지 않는다).
 * <pre>
 * &#64;Entity
 * public class GithubSyncJob extends RetryableJobEntity {
 *     &#64;Id &#64;GeneratedValue(strategy = GenerationType.IDENTITY)
 *     private Long id;
 *     private String kind;   // 작업 인자는 자식 엔티티가 정한다
 * }
 *
 * // 실행하는 쪽 (주기 작업). 꺼낼 때 잠가서 여러 대가 같은 행을 집지 않게 한다 (아래 저장소 예시)
 * job.start();
 * try {
 *     github.removeMember(...);
 *     job.succeed();
 * } catch (Exception e) {
 *     job.fail(e.getMessage(), POLICY);   // 다음 시도 시각을 정하거나, 다 썼으면 FAILED
 * }
 *
 * // 저장소: 실행할 차례인 행을 잠그고 꺼낸다 (lock timeout -2 = SKIP LOCKED, MySQL 8 · PostgreSQL)
 * &#64;Lock(LockModeType.PESSIMISTIC_WRITE)
 * &#64;QueryHints(&#64;QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
 * &#64;Query("select j from GithubSyncJob j where j.status = :status and j.nextRunAt &lt;= :now order by j.nextRunAt")
 * List&lt;GithubSyncJob&gt; findDue(RetryableJobStatus status, Instant now, Limit limit);   // status = PENDING
 * </pre>
 * 컬럼: {@code status}, {@code attempts}, {@code next_run_at}, {@code last_error}(최대 {@value #LAST_ERROR_MAX_LENGTH}자)
 * + {@link JobEntity} 의 {@code started_at}, {@code finished_at}, {@code created_at}. 인덱스 {@code (status, next_run_at)} 는
 * 자식 엔티티의 {@code @Table} 에 둔다.
 * <p>
 * 실행 중 서버가 죽으면 {@code RUNNING} 으로 남는다. 서비스는 시작한 지 오래된 {@code RUNNING} 행을
 * {@link #requeue(Instant)} 로 되돌리는 정리를 둔다. 같은 작업이 다시 실행될 수 있으므로 작업은 다시 실행해도 안전하게 만든다.
 */
@Getter
@MappedSuperclass
public abstract class RetryableJobEntity extends JobEntity<RetryableJobStatus> {

    public static final int LAST_ERROR_MAX_LENGTH = 1000;

    @Column(nullable = false)
    private int attempts;

    private Instant nextRunAt;

    @Column(length = LAST_ERROR_MAX_LENGTH)
    private String lastError;

    protected RetryableJobEntity() {
    }

    /**
     * runAt 부터 실행할 차례인 작업. 바로 실행하려면 {@code super(Instant.now())}.
     */
    protected RetryableJobEntity(Instant runAt) {
        super(RetryableJobStatus.PENDING);
        this.nextRunAt = runAt;
    }

    /**
     * now 에 실행할 차례인지 ({@code PENDING} 이고 다음 시도 시각이 지났음).
     */
    public boolean isDue(Instant now) {
        return getStatus() == RetryableJobStatus.PENDING && nextRunAt != null && !nextRunAt.isAfter(now);
    }

    /**
     * 실행을 시작한다. 시도 횟수를 하나 올린다.
     *
     * @throws IllegalStateException {@code PENDING} 이 아니면
     */
    public void start() {
        requireStatus(RetryableJobStatus.PENDING, "시작");
        attempts++;
        markRunning(RetryableJobStatus.RUNNING);
    }

    /**
     * 성공으로 끝낸다.
     *
     * @throws IllegalStateException {@code RUNNING} 이 아니면
     */
    public void succeed() {
        requireStatus(RetryableJobStatus.RUNNING, "성공 처리");
        nextRunAt = null;
        lastError = null;
        markFinished(RetryableJobStatus.SUCCEEDED);
    }

    public void fail(String error, BackoffPolicy policy) {
        fail(error, policy, Instant.now());
    }

    /**
     * 실패를 기록한다. 시도 횟수가 남았으면 {@code PENDING} 으로 돌리고 다음 시도 시각을 정하고,
     * 다 썼으면 {@code FAILED} 로 끝낸다. 오류 메시지는 {@value #LAST_ERROR_MAX_LENGTH}자로 자른다.
     * 비밀값 · 토큰이 들어 있을 수 있는 외부 응답 본문을 그대로 넣지 않는다.
     *
     * @throws IllegalStateException {@code RUNNING} 이 아니면
     */
    public void fail(String error, BackoffPolicy policy, Instant now) {
        requireStatus(RetryableJobStatus.RUNNING, "실패 처리");
        lastError = truncate(error);
        Optional<Instant> next = policy.nextAttemptAt(attempts, now);
        if (next.isPresent()) {
            nextRunAt = next.get();
            changeStatus(RetryableJobStatus.PENDING);
        } else {
            nextRunAt = null;
            markFinished(RetryableJobStatus.FAILED);
        }
    }

    /**
     * 사람이 "다시 시도"를 누를 때. {@code FAILED} 이거나 대기 중인 작업을 now 에 바로 실행할 차례로 돌리고
     * 시도 횟수를 0 으로 되돌린다 (재시도 규칙을 처음부터 다시 적용). 마지막 오류는 남긴다.
     *
     * @throws IllegalStateException 실행 중이거나 이미 성공했으면
     */
    public void retryNow(Instant now) {
        RetryableJobStatus status = getStatus();
        if (status != RetryableJobStatus.FAILED && status != RetryableJobStatus.PENDING) {
            throw new IllegalStateException("상태가 " + status + " 인 작업은 다시 시도할 수 없다");
        }
        attempts = 0;
        nextRunAt = now;
        reopen(RetryableJobStatus.PENDING);
    }

    /**
     * 실행 중에 멈춘 작업(서버 재시작 등)을 now 에 다시 실행할 차례로 돌린다. 시도 횟수는 그대로 둔다.
     *
     * @throws IllegalStateException {@code RUNNING} 이 아니면
     */
    public void requeue(Instant now) {
        requireStatus(RetryableJobStatus.RUNNING, "다시 대기");
        nextRunAt = now;
        changeStatus(RetryableJobStatus.PENDING);
    }

    private void requireStatus(RetryableJobStatus expected, String action) {
        if (getStatus() != expected) {
            throw new IllegalStateException("상태가 " + getStatus() + " 인 작업은 " + action + "할 수 없다 (" + expected + " 여야 함)");
        }
    }

    static String truncate(String error) {
        if (error == null || error.length() <= LAST_ERROR_MAX_LENGTH) {
            return error;
        }
        return error.substring(0, LAST_ERROR_MAX_LENGTH);
    }
}
