package com.pawpose.domain.match.pipeline;

import com.pawpose.domain.common.Species;
import com.pawpose.domain.lostpet.*;
import com.pawpose.domain.match.*;
import com.pawpose.domain.notice.*;
import com.pawpose.domain.notice.collection.NoticeCollectedEvent;
import com.pawpose.domain.user.*;
import com.pawpose.global.security.JwtProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.context.ApplicationEventPublisher;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class MatchingPipelineTest {
    @Autowired MatchingInputService inputs;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean MatchingSourceRepository sources;
    @Autowired MatchingWorker worker;
    @Autowired MatchingWorkService work;
    @Autowired MatchingJson json;
    @Autowired MatchingProperties properties;
    @Autowired LostPetRepository pets;
    @Autowired UserRepository users;
    @Autowired NoticeRepository notices;
    @Autowired MatchRepository matches;
    @Autowired MatchFeedbackService feedback;
    @Autowired CandidateNotificationRepository notifications;
    @Autowired CandidateQueryService queries;
    @Autowired TransactionTemplate tx;
    @Autowired ApplicationEventPublisher events;
    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoBean EmbeddingClient embedding;
    private User owner;
    private LostPet pet;
    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

    @BeforeEach void setup() {
        owner = users.save(User.local(UUID.randomUUID() + "@test.com", "unused", "test"));
        pet = pets.save(LostPet.create(owner, Species.CAT));
        when(embedding.embed(anyString())).thenReturn(new double[]{1, 0});
    }
    MatchingProfile profile(String image) { return new MatchingProfile(List.of(image), today.minusDays(2), "6110000", "3080000", "cat", "white", "street"); }
    MatchingSource source(SourceKind kind, long id) { return sources.findByKindAndSourceId(kind, id).orElseThrow(); }
    void lost() { inputs.registerLostPet(pet.getId(), profile("https://storage.example/lost.png")); }
    void sighting(long id) { inputs.registerSighting(id, Species.CAT, profile("https://storage.example/sighting.png")); worker.process(source(SourceKind.SIGHTING, id).getId()); }
    void runLost() { worker.process(source(SourceKind.LOST_PET, pet.getId()).getId()); }

    @Test void newLostMatchesExistingSightingAndPreservesFeedbackAndOneNotification() {
        long id = System.nanoTime(); sighting(id); lost(); runLost();
        Match match = matches.findByLostPetId(pet.getId()).stream().filter(m -> Objects.equals(m.getSightingId(), id)).findFirst().orElseThrow();
        feedback.giveFeedback(owner.getId(), match.getId(), MatchFeedback.NO);
        long count = notifications.count();
        lost(); runLost();
        assertThat(matches.findById(match.getId()).orElseThrow().getFeedback()).isEqualTo(MatchFeedback.NO);
        assertThat(matches.findByLostPetId(pet.getId()).stream().filter(m -> Objects.equals(m.getSightingId(), id)).count()).isEqualTo(1);
        assertThat(notifications.count()).isEqualTo(count);
        assertThat(queries.list(owner.getId(), pet.getId(), "similarity", 0, 20).items()).noneMatch(c -> c.matchId().equals(match.getId()));
    }

    @Test void newTargetQueuesExistingLostAndFoundStopsInflightResults() {
        lost(); runLost();
        long id = System.nanoTime(); sighting(id);
        MatchingSource source = source(SourceKind.LOST_PET, pet.getId());
        assertThat(source.getStatus()).isEqualTo(ProcessingStatus.PENDING);
        var claim = work.claim(source.getId()).orElseThrow();
        var scores = work.score(claim.source(), new double[][]{{1, 0}});
        inputs.markFound(owner.getId(), pet.getId());
        long count = notifications.count();
        work.completeLostPet(claim, new double[][]{{1, 0}}, scores);
        assertThat(notifications.count()).isEqualTo(count);
        assertThat(queries.list(owner.getId(), pet.getId(), "similarity", 0, 20).processingStatus()).isEqualTo(ProcessingStatus.CANCELLED);
    }

    @Test void leaseAndGenerationRejectObsoleteWorkAndRestartCanRecover() {
        lost(); MatchingSource source = source(SourceKind.LOST_PET, pet.getId());
        var old = work.claim(source.getId()).orElseThrow();
        assertThat(work.claim(source.getId())).isEmpty();
        lost();
        work.completeLostPet(old, new double[][]{{1, 0}}, List.of());
        assertThat(source(SourceKind.LOST_PET, pet.getId()).getStatus()).isEqualTo(ProcessingStatus.PENDING);
        MatchingSource detached = source(SourceKind.LOST_PET, pet.getId());
        assertThat(detached.claim("first", Instant.now())).isTrue();
        assertThat(detached.claim("recovered", Instant.now().plusSeconds(901))).isTrue();
        assertThat(detached.owned("first", detached.getGeneration())).isFalse();
    }

    @Test void permanentImageFailureVisibleAndManualRetryIsIdempotent() {
        lost(); when(embedding.embed(anyString())).thenThrow(new AnalysisException("UNSUPPORTED_IMAGE", false)); runLost();
        var state = queries.list(owner.getId(), pet.getId(), "similarity", 0, 20);
        assertThat(state.processingStatus()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(state.failureCode()).isEqualTo("UNSUPPORTED_IMAGE");
        inputs.retry(owner.getId(), pet.getId());
        long generation = source(SourceKind.LOST_PET, pet.getId()).getGeneration();
        inputs.retry(owner.getId(), pet.getId());
        assertThat(source(SourceKind.LOST_PET, pet.getId()).getGeneration()).isEqualTo(generation);
    }

    @Test void authenticationAndOwnershipAndInvalidPagination() throws Exception {
        lost();
        String path = "/api/v1/lost-pets/" + pet.getId() + "/matches";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", "Bearer " + jwt.createAccessToken(owner.getId()+100000)))
                .andExpect(status().isForbidden());
        String token = "Bearer " + jwt.createAccessToken(owner.getId());
        mvc.perform(get(path).header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processingStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.scoreIsProbability").value(false));
        mvc.perform(get(path).param("size", "21").header("Authorization", token)).andExpect(status().isBadRequest());
        mvc.perform(get(path).param("page", "-1").header("Authorization", token)).andExpect(status().isBadRequest());
        mvc.perform(post(path+"/retry").header("Authorization", "Bearer " + jwt.createAccessToken(owner.getId()+100000)))
                .andExpect(status().isForbidden());
    }

    @Test void yesFirstDeadlineSightingLastAndStablePagination() {
        var normal = notices.save(notice(today.plusDays(5)));
        var urgent = notices.save(notice(today.plusDays(1)));
        var expired = notices.save(notice(today.minusDays(1)));
        Match pin = matches.save(Match.ofNotice(pet, normal, 60));
        matches.save(Match.ofNotice(pet, urgent, 80));
        matches.save(Match.ofNotice(pet, expired, 99));
        matches.save(Match.ofSighting(pet, System.nanoTime(), 90));
        feedback.giveFeedback(owner.getId(), pin.getId(), MatchFeedback.YES);
        var result = queries.list(owner.getId(), pet.getId(), "deadline", 0, 20);
        assertThat(result.items()).extracting(c -> c.matchId()).startsWith(pin.getId());
        assertThat(result.items()).hasSize(3);
        assertThat(result.items().get(2).type()).isEqualTo(MatchType.SIGHTING);
        assertThat(queries.list(owner.getId(), pet.getId(), "deadline", 1, 1).items().get(0).noticeId()).isEqualTo(urgent.getId());
    }

    @Test void noticeEventAndSourceRollBackTogether() {
        Notice n = notices.save(notice(today.plusDays(3)));
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            events.publishEvent(new NoticeCollectedEvent(n.getId()));
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(sources.findByKindAndSourceId(SourceKind.NOTICE, n.getId())).isEmpty();
        tx.executeWithoutResult(status -> events.publishEvent(new NoticeCollectedEvent(n.getId())));
        assertThat(source(SourceKind.NOTICE, n.getId()).getStatus()).isEqualTo(ProcessingStatus.PENDING);
    }

    @Test void scoreUsesBestPhotoClampsAndRejectsInvalidVectors() {
        SimilarityScorer scorer = new SimilarityScorer();
        assertThat(scorer.score(new double[][]{{0,1},{1,0}}, new double[][]{{1,0}}, "same", "same", "white", "white", "1", "1").adjusted()).isEqualTo(1);
        assertThatThrownBy(() -> scorer.cosine(new double[]{0,0}, new double[]{1,0})).isInstanceOf(IllegalArgumentException.class);
        assertThat(RegionPolicy.nearby("서울특별시", "6410000")).isTrue();
        assertThat(RegionPolicy.nearby("6110000", "6500000")).isFalse();
    }

    @Test void concurrentCompletionCannotDuplicateCandidatesOrNotifications() throws Exception {
        long id = System.nanoTime(); sighting(id); lost();
        var claimed = work.claim(source(SourceKind.LOST_PET, pet.getId()).getId()).orElseThrow();
        var scores = work.score(claimed.source(), new double[][]{{1,0}});
        long count = notifications.count();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var start = new CountDownLatch(1);
            Callable<Void> save = () -> { start.await(); work.completeLostPet(claimed, new double[][]{{1,0}}, scores); return null; };
            var first = pool.submit(save); var second = pool.submit(save); start.countDown();
            first.get(15, TimeUnit.SECONDS); second.get(15, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertThat(matches.findByLostPetId(pet.getId()).stream().filter(m -> Objects.equals(m.getSightingId(), id)).count()).isEqualTo(1);
        assertThat(notifications.count() - count).isEqualTo(scores.size());
    }

    @Test void tenThousandCandidateRowsReturnOnlyTwentyWithinTwoSeconds() {
        List<Object[]> rows = new ArrayList<>();
        for (int i=0; i<10_000; i++) rows.add(new Object[]{pet.getId(), 900_000_000L+i, 60+(i%40)});
        jdbc.batchUpdate("insert into matches(lost_pet_id,sighting_id,similarity,type,feedback,eligible,created_at,updated_at) values(?,?,?,'SIGHTING','NONE',true,current_timestamp,current_timestamp)", rows);
        long started = System.nanoTime();
        var page = queries.list(owner.getId(), pet.getId(), "similarity", 0, 20);
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);
        assertThat(page.items()).hasSize(20); assertThat(page.totalCount()).isEqualTo(20);
        assertThat(millis).isLessThan(2000);
        System.out.println("candidate_query_10000_rows_ms=" + millis);
    }

    @Test void blockedImageLocationsNeverReachDownloader() {
        MatchingProperties config = new MatchingProperties(); config.setImageHosts(List.of("localhost", "www.animal.go.kr"));
        var client = new LocalEmbeddingClient(config, new com.fasterxml.jackson.databind.ObjectMapper());
        for (String url : List.of("file:///etc/passwd", "http://localhost/a.png", "https://evil.example/a.png", "https://user@www.animal.go.kr/a.png", "https://www.animal.go.kr:444/a.png")) {
            assertThatThrownBy(() -> client.download(url)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void unfinishedTargetsKeepPollingAndFailuresArePartialResults() {
        var island = new MatchingProfile(List.of("https://storage.example/island.png"), today, "6500000", "6510000", null, null, "island");
        inputs.registerLostPet(pet.getId(), island);
        long target = System.nanoTime(); inputs.registerSighting(target, Species.CAT, island);
        runLost();
        var waiting = queries.list(owner.getId(), pet.getId(), "similarity", 0, 20);
        assertThat(waiting.processingStatus()).isEqualTo(ProcessingStatus.PROCESSING);
        assertThat(waiting.pendingTargetCount()).isPositive();
        when(embedding.embed(anyString())).thenThrow(new AnalysisException("UNSUPPORTED_IMAGE", false));
        worker.process(source(SourceKind.SIGHTING, target).getId());
        var partial = queries.list(owner.getId(), pet.getId(), "similarity", 0, 20);
        assertThat(partial.processingStatus()).isEqualTo(ProcessingStatus.PARTIAL);
        assertThat(partial.failedTargetCount()).isPositive();
        assertThat(partial.processingMessage()).doesNotContain("아직 비슷한 공고가 없어요");
    }

    @Test void newTargetCannotResetPermanentFailureOrTransientBackoff() {
        lost(); when(embedding.embed(anyString())).thenThrow(new AnalysisException("UNSUPPORTED_IMAGE", false)); runLost();
        MatchingSource failed = source(SourceKind.LOST_PET, pet.getId());
        doReturn(new double[]{1,0}).when(embedding).embed(anyString()); sighting(System.nanoTime());
        MatchingSource after = source(SourceKind.LOST_PET, pet.getId());
        assertThat(after.getStatus()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(after.isAutomaticRetry()).isFalse();
        assertThat(after.getAvailableAt()).isEqualTo(failed.getAvailableAt());
        inputs.retry(owner.getId(), pet.getId());
        when(embedding.embed(anyString())).thenThrow(new AnalysisException("EMBEDDING_HTTP_503", true)); runLost();
        MatchingSource delayed = source(SourceKind.LOST_PET, pet.getId());
        doReturn(new double[]{1,0}).when(embedding).embed(anyString()); sighting(System.nanoTime());
        after = source(SourceKind.LOST_PET, pet.getId());
        assertThat(after.getAttempts()).isEqualTo(delayed.getAttempts());
        assertThat(after.getAvailableAt()).isEqualTo(delayed.getAvailableAt());
        assertThat(after.getErrorCode()).isEqualTo(delayed.getErrorCode());
    }

    @Test void targetFinishingDuringLostAnalysisSchedulesAnotherComparisonWithoutDiscardingVectors() {
        lost(); var claim = work.claim(source(SourceKind.LOST_PET, pet.getId()).getId()).orElseThrow();
        sighting(System.nanoTime());
        work.completeLostPet(claim, new double[][]{{1,0}}, List.of());
        MatchingSource after = source(SourceKind.LOST_PET, pet.getId());
        assertThat(after.getStatus()).isEqualTo(ProcessingStatus.PENDING);
        assertThat(after.getVectorsJson()).isNotNull();
    }

    @Test void automaticFanoutReadsProfileOnlyAfterAcquiringLock() throws Exception {
        lost();
        long target = System.nanoTime(); inputs.registerSighting(target, Species.CAT, profile("https://storage.example/target.png"));
        var targetWork = work.claim(source(SourceKind.SIGHTING, target).getId()).orElseThrow();
        CountDownLatch idsRead = new CountDownLatch(1), updated = new CountDownLatch(1);
        List<Long> selectedIds = List.of(source(SourceKind.LOST_PET, pet.getId()).getId());
        doAnswer(invocation -> {
            idsRead.countDown();
            if (!updated.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("update timeout");
            return selectedIds;
        }).when(sources).activeSourceIds(SourceKind.LOST_PET);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> fanout = executor.submit(() -> work.completeTarget(targetWork, new double[][]{{1,0}}));
            assertThat(idsRead.await(10, TimeUnit.SECONDS)).isTrue();
            inputs.registerLostPet(pet.getId(), profile("https://storage.example/new-photo.png"));
            long generation = source(SourceKind.LOST_PET, pet.getId()).getGeneration();
            updated.countDown(); fanout.get(15, TimeUnit.SECONDS);
            var latest = source(SourceKind.LOST_PET, pet.getId());
            assertThat(latest.getImagesJson()).contains("new-photo.png");
            assertThat(latest.getGeneration()).isEqualTo(generation);
        } finally { updated.countDown(); executor.shutdownNow(); }
    }

    @Test void rescueBeforeDisappearanceIsNotAdmittedByLaterNoticeDate() {
        lost();
        Notice before = notices.save(Notice.builder().desertionNo(UUID.randomUUID().toString().substring(0,25)).species(Species.CAT)
                .happenDate(today.minusDays(3)).noticeStartDate(today).noticeEndDate(today.plusDays(7))
                .imageUrl("https://www.animal.go.kr/old.png").status(NoticeStatus.NOTICE).build());
        tx.executeWithoutResult(s -> events.publishEvent(new NoticeCollectedEvent(before.getId())));
        assertThat(source(SourceKind.NOTICE, before.getId()).getOccurredOn()).isEqualTo(today.minusDays(3));
        worker.process(source(SourceKind.NOTICE, before.getId()).getId()); runLost();
        assertThat(matches.findByLostPetId(pet.getId())).noneMatch(m -> m.getNotice()!=null && m.getNotice().getId().equals(before.getId()));
    }

    private Notice notice(LocalDate end) {
        return Notice.builder().desertionNo(UUID.randomUUID().toString().substring(0,25)).species(Species.CAT)
                .noticeStartDate(today).noticeEndDate(end).happenDate(today).imageUrl("https://www.animal.go.kr/test.png")
                .orgName("서울특별시 강북구").status(NoticeStatus.NOTICE).build();
    }
}
