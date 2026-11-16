package com.pawpose.domain.match.pipeline;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;

@Service @RequiredArgsConstructor
public class ComparisonDispatchService {
    private final ComparisonRequestRepository requests;
    private final MatchingSourceRepository sources;

    /** Called by the worker outside target-save transactions. Never locks target sources. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void dispatchPending() {
        List<ComparisonRequest> batch = new ArrayList<>();
        for (Long id : requests.pendingIds(PageRequest.of(0, 100))) {
            requests.lockById(id).ifPresent(batch::add);
        }
        if (batch.isEmpty()) return;
        // The target is committed before we snapshot lost IDs. A later registration sees
        // that target on its initial comparison. Existing in-flight work is requeued.
        for (Long id : sources.activeSourceIds(SourceKind.LOST_PET)) {
            sources.lockById(id).orElseThrow().requestComparison();
        }
        // Requeue and acknowledgement commit together; rollback leaves the requests retryable.
        requests.deleteAll(batch);
    }
}
