package com.pawpose.domain.notice.collection;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CollectionStateStore {
    private final CollectionStateRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void initialize() {
        if (!repository.existsById(CollectionState.SINGLETON_ID)) {
            repository.saveAndFlush(CollectionState.initial());
        }
    }

    @Transactional
    public Optional<Run> acquire(LocalDateTime now) {
        CollectionState state = locked();
        if (!state.canAcquire(now)) return Optional.empty();
        String token = UUID.randomUUID().toString();
        state.acquire(token, now);
        boolean initial = state.getCompletedThrough() == null;
        LocalDate from = initial ? now.toLocalDate().minusDays(29) : state.getCompletedThrough().minusDays(1);
        return Optional.of(new Run(token, from, now.toLocalDate(), initial));
    }

    @Transactional
    public void reserveRequest(String token, LocalDateTime now, int dailyLimit) {
        CollectionState state = locked();
        state.checkLease(token, now);
        state.resetQuota(now.toLocalDate());
        if (state.getRequestCount() >= dailyLimit) {
            throw new CollectionFailure(CollectionFailure.Reason.QUOTA);
        }
        state.reserveRequest(now);
    }

    @Transactional
    public void finish(Run run, LocalDateTime now, String failure, int slowdownThreshold, int dailyLimit) {
        CollectionState state = locked();
        if (failure == null) state.checkLease(run.token(), now);
        LocalDateTime next = now.plusMinutes(30);
        if ("QUOTA".equals(failure) || state.getRequestCount() >= slowdownThreshold) next = now.plusHours(2);
        if (state.getRequestCount() >= dailyLimit) next = now.toLocalDate().plusDays(1).atStartOfDay();
        state.finish(run.token(), run.through(), failure, next);
    }

    CollectionState locked() {
        return repository.lockState().orElseThrow(() -> new IllegalStateException("Collection state missing"));
    }

    public record Run(String token, LocalDate from, LocalDate through, boolean initial) {
    }
}
