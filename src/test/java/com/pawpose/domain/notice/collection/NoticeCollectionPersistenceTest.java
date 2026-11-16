package com.pawpose.domain.notice.collection;

import com.pawpose.domain.common.Sex;
import com.pawpose.domain.common.Species;
import com.pawpose.domain.notice.NoticeRepository;
import com.pawpose.domain.notice.NoticeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import({CollectionStateStore.class, NoticePageStore.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@RecordApplicationEvents
class NoticeCollectionPersistenceTest {
    @Autowired CollectionStateStore stateStore;
    @Autowired CollectionStateRepository states;
    @Autowired NoticePageStore pages;
    @Autowired NoticeRepository notices;
    @Autowired ApplicationEvents events;
    private final LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

    @BeforeEach
    void clear() {
        notices.deleteAll();
        states.deleteAll();
        events.clear();
    }

    @Test
    void upsertUpdatesPhotoAndClosedStatusWithoutDuplicateRowsOrDuplicateEvents() {
        stateStore.initialize();
        var run = stateStore.acquire(now).orElseThrow();
        var first = item("1", "https://example.test/one.jpg", NoticeStatus.NOTICE);
        assertThat(pages.save(run.token(), List.of(first), now)).isEqualTo(1);
        assertThat(pages.save(run.token(), List.of(first), now.plusSeconds(1))).isZero();
        assertThat(pages.save(run.token(), List.of(item("1", "https://example.test/two.jpg", NoticeStatus.RETURNED)),
                now.plusSeconds(2))).isEqualTo(1);
        assertThat(notices.count()).isEqualTo(1);
        var saved = notices.findByDesertionNo("1").orElseThrow();
        assertThat(saved.getImageUrl()).isEqualTo("https://example.test/two.jpg");
        assertThat(saved.getStatus()).isEqualTo(NoticeStatus.RETURNED);
        assertThat(events.stream(NoticeCollectedEvent.class).count()).isEqualTo(2);
    }

    @Test
    void pageFailurePreservesPreviousCheckpointAndRetriesOnlyTransientErrors() {
        stateStore.initialize();
        var prior = stateStore.acquire(now.minusDays(2)).orElseThrow();
        stateStore.finish(prior, now.minusDays(2), null, 9000, 10000);
        var api = mock(PublicAnimalApiClient.class);
        when(api.fetch(any(), any(), eq(false), eq(1))).thenReturn(new PublicAnimalApiClient.Page(
                List.of(item("1", "https://example.test/one.jpg", NoticeStatus.NOTICE)), 2));
        when(api.fetch(any(), any(), eq(false), eq(2)))
                .thenThrow(new CollectionFailure(CollectionFailure.Reason.TRANSIENT));
        var result = service(api).collectOnce();
        assertThat(result.status()).isEqualTo("TRANSIENT");
        assertThat(notices.count()).isEqualTo(1);
        CollectionState state = states.findById(1L).orElseThrow();
        assertThat(state.getCompletedThrough()).isEqualTo(prior.through());
        assertThat(state.getRequestCount()).isEqualTo(5); // page 1 + initial page 2 call + 3 retries
        verify(api, times(4)).fetch(eq(prior.through().minusDays(1)), any(), eq(false), eq(2));
        assertThat(state.getLeaseToken()).isNull();
    }

    @Test
    void fullSuccessAdvancesCheckpointAfterAllPages() {
        var api = mock(PublicAnimalApiClient.class);
        when(api.fetch(any(), any(), eq(true), eq(1))).thenReturn(new PublicAnimalApiClient.Page(
                List.of(item("1", "https://example.test/one.jpg", NoticeStatus.NOTICE)), 2));
        when(api.fetch(any(), any(), eq(true), eq(2))).thenReturn(new PublicAnimalApiClient.Page(
                List.of(item("2", "https://example.test/two.jpg", NoticeStatus.NOTICE)), 2));
        assertThat(service(api).collectOnce().status()).isEqualTo("COMPLETED");
        CollectionState state = states.findById(1L).orElseThrow();
        assertThat(state.getCompletedThrough()).isEqualTo(LocalDate.now(ZoneId.of("Asia/Seoul")));
        assertThat(notices.count()).isEqualTo(2);
        verify(api).fetch(eq(state.getCompletedThrough().minusDays(29)), eq(state.getCompletedThrough()), eq(true), eq(1));
    }

    @Test
    void leasePreventsOverlapAndExpiredOwnerCannotWrite() {
        stateStore.initialize();
        var first = stateStore.acquire(now).orElseThrow();
        assertThat(stateStore.acquire(now.plusSeconds(1))).isEmpty();
        var recovered = stateStore.acquire(now.plusMinutes(6)).orElseThrow();
        assertThat(recovered.token()).isNotEqualTo(first.token());
        assertThatThrownBy(() -> pages.save(first.token(), List.of(item("old", null, NoticeStatus.NOTICE)), now.plusMinutes(6)))
                .isInstanceOf(CollectionFailure.class).hasMessage("LEASE_LOST");
        assertThat(notices.count()).isZero();
    }

    @Test
    void quotaIsCountedBeforeCallingProviderAndDefersWithoutCheckpoint() {
        var api = mock(PublicAnimalApiClient.class);
        when(api.fetch(any(), any(), anyBoolean(), anyInt())).thenReturn(new PublicAnimalApiClient.Page(
                List.of(item("1", null, NoticeStatus.NOTICE)), 2));
        CollectionProperties properties = properties();
        properties.setDailyRequestLimit(1);
        properties.setSlowdownThreshold(1);
        var service = new NoticeCollectionService(properties, api, stateStore, pages);
        assertThat(service.collectOnce().status()).isEqualTo("QUOTA");
        verify(api, times(1)).fetch(any(), any(), anyBoolean(), anyInt());
        var state = states.findById(1L).orElseThrow();
        assertThat(state.getCompletedThrough()).isNull();
        assertThat(state.getNextAttemptAt()).isEqualTo(LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1).atStartOfDay());
    }

    @Test
    void changingFeedCountCanCompleteAndDistrictLookupIsCachedAndBudgeted() {
        var api = mock(PublicAnimalApiClient.class);
        when(api.fetch(any(), any(), eq(true), eq(1))).thenReturn(new PublicAnimalApiClient.Page(
                List.of(item("1", null, NoticeStatus.NOTICE).withSigunguCode(null)), 3));
        when(api.fetch(any(), any(), eq(true), eq(2))).thenReturn(new PublicAnimalApiClient.Page(
                List.of(item("2", null, NoticeStatus.NOTICE).withSigunguCode(null)), 2));
        when(api.fetchDistricts("6110000")).thenReturn(Map.of("강북구", "3080000"));
        assertThat(service(api).collectOnce().status()).isEqualTo("COMPLETED");
        assertThat(notices.findByDesertionNo("1").orElseThrow().getSigunguCode()).isEqualTo("3080000");
        assertThat(notices.findByDesertionNo("2").orElseThrow().getSigunguCode()).isEqualTo("3080000");
        verify(api, times(1)).fetchDistricts("6110000");
        assertThat(states.findById(1L).orElseThrow().getRequestCount()).isEqualTo(3);
    }

    private NoticeCollectionService service(PublicAnimalApiClient api) {
        return new NoticeCollectionService(properties(), api, stateStore, pages);
    }

    private CollectionProperties properties() {
        CollectionProperties p = new CollectionProperties();
        p.setEnabled(true);
        p.setServiceKey("test-key-not-real");
        p.setPageSize(1);
        p.setRetryDelayMillis(0);
        return p;
    }

    private CollectedNotice item(String id, String image, NoticeStatus status) {
        return new CollectedNotice(id, "notice-" + id, now.toLocalDate(), now.toLocalDate().plusDays(10),
                now.toLocalDate(), "서울 강북구", Species.CAT, "한국 고양이", "흰색", Sex.UNKNOWN,
                null, null, null, "보호소", null, null, "서울특별시 강북구", image, status, "6110000", "3080000");
    }
}
