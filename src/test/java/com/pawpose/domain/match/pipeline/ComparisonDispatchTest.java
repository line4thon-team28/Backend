package com.pawpose.domain.match.pipeline;

import com.pawpose.domain.common.Species;
import com.pawpose.domain.lostpet.*;
import com.pawpose.domain.match.*;
import com.pawpose.domain.notice.*;
import com.pawpose.domain.notice.collection.NoticeCollectedEvent;
import com.pawpose.domain.user.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "spring.jpa.show-sql=false")
class ComparisonDispatchTest {
    @Autowired MatchingInputService inputs;
    @Autowired MatchingWorkService work;
    @Autowired MatchingWorker worker;
    @Autowired MatchingProperties properties;
    @Autowired ComparisonDispatchService comparisons;
    @MockitoSpyBean MatchingSourceRepository sources;
    @MockitoSpyBean ComparisonRequestRepository requests;
    @Autowired LostPetRepository pets;
    @Autowired UserRepository users;
    @Autowired NoticeRepository notices;
    @Autowired MatchRepository matches;
    @Autowired CandidateNotificationRepository notifications;
    @Autowired CandidateQueryService queries;
    @Autowired TransactionTemplate tx;
    @Autowired ApplicationEventPublisher events;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoBean EmbeddingClient embedding;
    private User owner;
    private LostPet pet;
    private final double[][] vectors = {{1, 0}};

    @BeforeEach void setup() {
        notifications.deleteAllInBatch();
        matches.deleteAllInBatch();
        requests.deleteAllInBatch();
        sources.deleteAllInBatch();
        owner = users.save(User.local(UUID.randomUUID() + "@test.com", "unused", "test"));
        pet = pets.save(LostPet.create(owner, Species.CAT));
        when(embedding.embed(anyString())).thenReturn(vectors[0]);
    }

    @Test void registrationDuringUncommittedTargetCompletionIsEventuallyCompared() throws Exception {
        var target = target();
        CountDownLatch saved = new CountDownLatch(1), commit = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> completion = executor.submit(() -> tx.executeWithoutResult(status -> {
                work.completeTarget(target, vectors);
                saved.countDown();
                await(commit);
            }));
            await(saved);
            // Another worker finishes a registration while the target is still uncommitted.
            inputs.registerLostPet(pet.getId(), profile());
            var lost = work.claim(lostSource().getId()).orElseThrow();
            var scores = work.score(lost.source(), vectors);
            assertThat(scores).isEmpty();
            work.completeLostPet(lost, vectors, scores);
            assertThat(requests.count()).isZero();
            commit.countDown();
            completion.get(15, TimeUnit.SECONDS);

            assertThat(lostSource().getStatus()).isEqualTo(ProcessingStatus.READY);
            assertThat(requests.count()).isEqualTo(1);
            var waiting = queries.list(owner.getId(), pet.getId(), "similarity", 0, 20);
            assertThat(waiting.processingStatus()).isEqualTo(ProcessingStatus.PROCESSING);
            assertThat(waiting.retryAfterSeconds()).isEqualTo(10);

            // No in-memory callback is needed: a later worker tick consumes the persisted row.
            comparisons.dispatchPending();
            worker.process(lostSource().getId());
            assertThat(matches.findByLostPetId(pet.getId())).hasSize(1);
            assertThat(notifications.count()).isEqualTo(1);
            assertThat(requests.count()).isZero();
            assertThat(queries.list(owner.getId(), pet.getId(), "similarity", 0, 20).processingStatus()).isEqualTo(ProcessingStatus.READY);
            comparisons.dispatchPending();
            assertThat(lostSource().getStatus()).isEqualTo(ProcessingStatus.READY);
            assertThat(notifications.count()).isEqualTo(1);
        } finally { commit.countDown(); executor.shutdownNow(); }
    }

    @Test void registrationAfterDispatchSnapshotSeesAlreadyCommittedTarget() throws Exception {
        work.completeTarget(target(), vectors);
        CountDownLatch snapshot = new CountDownLatch(1), registered = new CountDownLatch(1);
        doAnswer(invocation -> {
            snapshot.countDown();
            await(registered);
            return List.of();
        }).when(sources).activeSourceIds(SourceKind.LOST_PET);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> dispatch = executor.submit(comparisons::dispatchPending);
            await(snapshot);
            inputs.registerLostPet(pet.getId(), profile());
            worker.process(lostSource().getId());
            registered.countDown();
            dispatch.get(15, TimeUnit.SECONDS);
            assertThat(matches.findByLostPetId(pet.getId())).hasSize(1);
            assertThat(requests.count()).isZero();
        } finally { registered.countDown(); executor.shutdownNow(); }
    }

    @Test void closingThenUpdatingTargetsDoesNotInvertLocksWithTargetCompletion() throws Exception {
        inputs.registerLostPet(pet.getId(), profile());
        worker.process(lostSource().getId());
        Notice a = notices.save(notice());
        Notice b = notices.save(notice());
        tx.executeWithoutResult(status -> {
            events.publishEvent(new NoticeCollectedEvent(a.getId()));
            events.publishEvent(new NoticeCollectedEvent(b.getId()));
        });
        var targetB = work.claim(source(SourceKind.NOTICE, b.getId()).getId()).orElseThrow();
        CountDownLatch closedA = new CountDownLatch(1), lockedB = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> collection = executor.submit(() -> tx.executeWithoutResult(status -> {
                notices.findById(a.getId()).orElseThrow().changeStatus(NoticeStatus.ENDED);
                // The same ordered callbacks that a two-notice page invokes BEFORE_COMMIT.
                inputs.noticeCollected(new NoticeCollectedEvent(a.getId()));
                closedA.countDown();
                await(lockedB);
                inputs.noticeCollected(new NoticeCollectedEvent(b.getId()));
            }));
            Future<?> completion = executor.submit(() -> {
                await(closedA);
                tx.executeWithoutResult(status -> {
                    sources.lockById(targetB.source().getId()).orElseThrow();
                    lockedB.countDown();
                    // Previously waited for the lost row held by A's callback while that
                    // callback's transaction waited for B: both now finish without retry.
                    work.completeTarget(targetB, vectors);
                });
            });
            collection.get(15, TimeUnit.SECONDS);
            completion.get(15, TimeUnit.SECONDS);
            assertThat(source(SourceKind.NOTICE, a.getId()).getStatus()).isEqualTo(ProcessingStatus.CANCELLED);
            assertThat(source(SourceKind.NOTICE, b.getId()).getStatus()).isEqualTo(ProcessingStatus.PENDING);
            assertThat(requests.count()).isEqualTo(2);
            comparisons.dispatchPending();
            assertThat(lostSource().getStatus()).isEqualTo(ProcessingStatus.PENDING);
            assertThat(requests.count()).isZero();
        } finally { closedA.countDown(); lockedB.countDown(); executor.shutdownNow(); }
    }

    @Test void rollbackKeepsDispatchRequestAndLostStateForRetry() {
        inputs.registerLostPet(pet.getId(), profile());
        worker.process(lostSource().getId());
        long generation = lostSource().getGeneration();
        work.completeTarget(target(), vectors);
        doThrow(new IllegalStateException("acknowledgement failed")).when(requests).deleteAll(anyList());
        assertThatThrownBy(comparisons::dispatchPending).isInstanceOf(IllegalStateException.class);
        assertThat(requests.count()).isEqualTo(1);
        assertThat(lostSource().getStatus()).isEqualTo(ProcessingStatus.READY);
        assertThat(lostSource().getGeneration()).isEqualTo(generation);
        reset(requests);
        comparisons.dispatchPending();
        assertThat(requests.count()).isZero();
        assertThat(lostSource().getGeneration()).isEqualTo(generation + 1);
    }

    @Test void targetAndClosureRequestsRollBackWithTheirSourceChanges() {
        var target = target();
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            work.completeTarget(target, vectors);
            throw new IllegalStateException("target rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(sources.findById(target.source().getId()).orElseThrow().getStatus()).isEqualTo(ProcessingStatus.PROCESSING);
        assertThat(requests.count()).isZero();
        Notice notice = notices.save(notice());
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            notices.findById(notice.getId()).orElseThrow().changeStatus(NoticeStatus.ENDED);
            inputs.noticeCollected(new NoticeCollectedEvent(notice.getId()));
            throw new IllegalStateException("closure rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(sourceOptional(SourceKind.NOTICE, notice.getId())).isEmpty();
        assertThat(requests.count()).isZero();
        assertThat(notices.findById(notice.getId()).orElseThrow().getStatus()).isEqualTo(NoticeStatus.NOTICE);
    }

    @Test void twoDispatchersConsumeTheSameBatchOnlyOnce() throws Exception {
        inputs.registerLostPet(pet.getId(), profile());
        worker.process(lostSource().getId());
        work.completeTarget(target(), vectors);
        long generation = lostSource().getGeneration();
        List<Long> ids = requests.pendingIds(org.springframework.data.domain.PageRequest.of(0, 100));
        CyclicBarrier selected = new CyclicBarrier(2);
        doAnswer(invocation -> { selected.await(10, TimeUnit.SECONDS); return ids; })
                .when(requests).pendingIds(any());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(comparisons::dispatchPending);
            Future<?> second = executor.submit(comparisons::dispatchPending);
            first.get(15, TimeUnit.SECONDS); second.get(15, TimeUnit.SECONDS);
            assertThat(requests.count()).isZero();
            assertThat(lostSource().getGeneration()).isEqualTo(generation + 1);
            verify(sources, times(1)).activeSourceIds(SourceKind.LOST_PET);
        } finally { executor.shutdownNow(); }
    }

    @Test void workerTickRecoversPersistedRequestsWithoutRepeatingInference() {
        inputs.registerLostPet(pet.getId(), profile());
        worker.process(lostSource().getId());
        work.completeTarget(target(), vectors);
        clearInvocations(embedding);
        try {
            properties.setEnabled(true);
            worker.poll();
        } finally { properties.setEnabled(false); }
        assertThat(requests.count()).isZero();
        assertThat(matches.findByLostPetId(pet.getId())).hasSize(1);
        verifyNoInteractions(embedding);
    }

    @Test void statusQueryKeepsPollingWhenDispatchCommitsBetweenItsReads() throws Exception {
        inputs.registerLostPet(pet.getId(), profile());
        worker.process(lostSource().getId());
        work.completeTarget(target(), vectors);
        CountDownLatch stateRead = new CountDownLatch(1), dispatched = new CountDownLatch(1);
        Long lostId = lostSource().getId();
        doAnswer(invocation -> {
            stateRead.countDown();
            await(dispatched);
            // Execute the scalar snapshot on the query transaction's actual connection.
            return Optional.ofNullable(jdbc.queryForObject(
                    "select status, error_code, exists(select 1 from matching_comparison_request) as queued from matching_source where id = ?",
                    (rs, row) -> new MatchingProgress(ProcessingStatus.valueOf(rs.getString("status")), rs.getString("error_code"), rs.getBoolean("queued")), lostId));
        }).when(sources).progress(lostId);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            var response = executor.submit(() -> queries.list(owner.getId(), pet.getId(), "similarity", 0, 20));
            await(stateRead);
            comparisons.dispatchPending();
            assertThat(requests.count()).isZero();
            assertThat(lostSource().getStatus()).isEqualTo(ProcessingStatus.PENDING);
            dispatched.countDown();
            var page = response.get(15, TimeUnit.SECONDS);
            assertThat(page.processingStatus()).isEqualTo(ProcessingStatus.PENDING);
            assertThat(page.retryAfterSeconds()).isEqualTo(10);
            assertThat(queries.list(owner.getId(), pet.getId(), "similarity", 0, 20).processingStatus()).isEqualTo(ProcessingStatus.PENDING);
        } finally { dispatched.countDown(); executor.shutdownNow(); }
    }

    private MatchingWorkService.Work target() {
        long id = System.nanoTime();
        inputs.registerSighting(id, Species.CAT, profile());
        return work.claim(source(SourceKind.SIGHTING, id).getId()).orElseThrow();
    }
    private MatchingSource lostSource() { return source(SourceKind.LOST_PET, pet.getId()); }
    private MatchingSource source(SourceKind kind, long id) { return sourceOptional(kind, id).orElseThrow(); }
    private Optional<MatchingSource> sourceOptional(SourceKind kind, long id) { return sources.findByKindAndSourceId(kind, id); }
    private MatchingProfile profile() {
        return new MatchingProfile(List.of("https://storage.example/test.png"), LocalDate.now(ZoneId.of("Asia/Seoul")), "6500000", "6510000", null, null, "test");
    }
    private Notice notice() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        return Notice.builder().desertionNo(UUID.randomUUID().toString().substring(0,25)).species(Species.CAT)
                .happenDate(today).noticeStartDate(today).noticeEndDate(today.plusDays(7))
                .imageUrl("https://www.animal.go.kr/test.png").status(NoticeStatus.NOTICE).build();
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("barrier timeout"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
}
