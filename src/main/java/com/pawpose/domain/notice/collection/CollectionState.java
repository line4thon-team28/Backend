package com.pawpose.domain.notice.collection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "notice_collection_state")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionState {
    static final long SINGLETON_ID = 1L;
    @Id
    private Long id;
    private LocalDate completedThrough;
    private LocalDate quotaDate;
    private int requestCount;
    @Column(length = 36)
    private String leaseToken;
    private LocalDateTime leaseUntil;
    private LocalDateTime nextAttemptAt;
    @Column(length = 40)
    private String lastFailure;

    static CollectionState initial() {
        CollectionState state = new CollectionState();
        state.id = SINGLETON_ID;
        return state;
    }

    boolean canAcquire(LocalDateTime now) {
        return (leaseUntil == null || !leaseUntil.isAfter(now))
                && (nextAttemptAt == null || !nextAttemptAt.isAfter(now));
    }

    void acquire(String token, LocalDateTime now) {
        leaseToken = token;
        leaseUntil = now.plusMinutes(5);
        resetQuota(now.toLocalDate());
    }

    void checkLease(String token, LocalDateTime now) {
        if (!token.equals(leaseToken) || leaseUntil == null || !leaseUntil.isAfter(now)) {
            throw new CollectionFailure(CollectionFailure.Reason.LEASE_LOST);
        }
    }

    void reserveRequest(LocalDateTime now) {
        resetQuota(now.toLocalDate());
        requestCount++;
        leaseUntil = now.plusMinutes(5);
    }

    void renew(LocalDateTime now) {
        leaseUntil = now.plusMinutes(5);
    }

    void finish(String token, LocalDate through, String failure, LocalDateTime nextAttempt) {
        if (!token.equals(leaseToken)) return;
        if (failure == null) completedThrough = through;
        lastFailure = failure;
        nextAttemptAt = nextAttempt;
        leaseToken = null;
        leaseUntil = null;
    }

    void resetQuota(LocalDate today) {
        if (!today.equals(quotaDate)) {
            quotaDate = today;
            requestCount = 0;
        }
    }
}
