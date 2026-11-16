package com.pawpose.domain.notice.collection;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.LocalDate;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeCollectionService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final CollectionProperties properties;
    private final PublicAnimalApiClient client;
    private final CollectionStateStore stateStore;
    private final NoticePageStore pageStore;
    private final Map<String, DistrictCache> districts = new ConcurrentHashMap<>();

    @PostConstruct
    void validateConfiguration() {
        properties.validate();
    }

    /** Polling is cheap; DB nextAttemptAt controls the actual 30-minute / 2-hour interval. */
    @Scheduled(fixedDelayString = "${pawpose.collection.poll-delay-ms:60000}",
            initialDelayString = "${pawpose.collection.initial-delay-ms:10000}")
    public void scheduledCollect() {
        try {
            collectOnce();
        } catch (RuntimeException e) {
            // No exception message: HTTP client exceptions may contain the service key in a URL.
            log.warn("Notice collection could not access its persisted state; next poll will retry");
        }
    }

    public Result collectOnce() {
        if (!properties.isEnabled()) return new Result("DISABLED", 0);
        properties.validate();
        try {
            stateStore.initialize();
        } catch (DataIntegrityViolationException concurrentInitialization) {
            // Another instance inserted singleton row. Its independent initialization transaction won.
        }
        var acquired = stateStore.acquire(now());
        if (acquired.isEmpty()) return new Result("DEFERRED", 0);
        CollectionStateStore.Run run = acquired.get();
        int changed = 0;
        try {
            for (int page = 1; ; page++) {
                var result = fetchWithRetry(run, page);
                List<CollectedNotice> resolved = result.notices().stream().map(n -> resolveDistrict(run, n)).toList();
                changed += pageStore.save(run.token(), resolved, now());
                // The feed is mutable. A shrinking final page is valid; next run overlaps today's updates.
                if ((long) page * properties.getPageSize() >= result.totalCount()) break;
                if (page >= 10_000) throw new CollectionFailure(CollectionFailure.Reason.QUOTA);
            }
            stateStore.finish(run, now(), null, properties.getSlowdownThreshold(), properties.getDailyRequestLimit());
            log.info("Notice collection completed: {} changed notices", changed);
            return new Result("COMPLETED", changed);
        } catch (RuntimeException e) {
            String failure = e instanceof CollectionFailure f ? f.reason().name() : "PERSISTENCE_FAILURE";
            stateStore.finish(run, now(), failure, properties.getSlowdownThreshold(), properties.getDailyRequestLimit());
            log.warn("Notice collection deferred: {}", failure);
            return new Result(failure, changed);
        }
    }

    private PublicAnimalApiClient.Page fetchWithRetry(CollectionStateStore.Run run, int page) {
        return withRetry(run, () -> client.fetch(run.from(), run.through(), run.initial(), page));
    }

    private <T> T withRetry(CollectionStateStore.Run run, Supplier<T> request) {
        for (int attempt = 0; ; attempt++) {
            stateStore.reserveRequest(run.token(), now(), properties.getDailyRequestLimit());
            try {
                return request.get();
            } catch (CollectionFailure e) {
                if (e.reason() != CollectionFailure.Reason.TRANSIENT || attempt >= properties.getMaxRetries()) throw e;
                try {
                    Thread.sleep(properties.getRetryDelayMillis() * (attempt + 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    private CollectedNotice resolveDistrict(CollectionStateStore.Run run, CollectedNotice notice) {
        if (notice.sigunguCode() != null || notice.sidoCode() == null || notice.orgName() == null) return notice;
        LocalDate today = now().toLocalDate();
        DistrictCache cached = districts.get(notice.sidoCode());
        if (cached == null || !today.equals(cached.date())) {
            cached = new DistrictCache(today, withRetry(run, () -> client.fetchDistricts(notice.sidoCode())));
            districts.put(notice.sidoCode(), cached);
        }
        String fullName = notice.orgName().strip();
        int separator = fullName.indexOf(' ');
        String district = separator < 0 ? fullName : fullName.substring(separator + 1).strip();
        String code = cached.codes().get(district);
        return code == null ? notice : notice.withSigunguCode(code);
    }

    private record DistrictCache(LocalDate date, Map<String, String> codes) {
    }

    private LocalDateTime now() {
        return LocalDateTime.now(SEOUL);
    }

    public record Result(String status, int changedNotices) {
    }
}
