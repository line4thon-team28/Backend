package com.pawpose.domain.match.pipeline;

import com.pawpose.domain.lostpet.*;
import com.pawpose.domain.match.*;
import com.pawpose.domain.notice.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class MatchingWorkService {
    private final MatchingSourceRepository sources;
    private final ComparisonRequestRepository comparisonRequests;
    private final MatchingProperties properties;
    private final MatchingJson json;
    private final LostPetRepository lostPets;
    private final NoticeRepository notices;
    private final MatchRepository matches;
    private final CandidateNotificationRepository notifications;
    private final SimilarityScorer scorer;

    @Transactional
    public void requeueOutdatedModels() {
        for (Long id : sources.outdatedSourceIds(properties.getModelVersion())) {
            MatchingSource locked = sources.lockById(id).orElseThrow();
            if (locked.isActive() && locked.getStatus() == ProcessingStatus.READY && !properties.getModelVersion().equals(locked.getModelVersion())) locked.enqueue();
        }
    }

    @Transactional
    public Optional<Work> claim(Long id) {
        MatchingSource s = sources.lockById(id).orElse(null);
        String token = UUID.randomUUID().toString();
        if (s == null || !s.claim(token, Instant.now())) return Optional.empty();
        return Optional.of(new Work(s, token, s.getGeneration()));
    }

    @Transactional(readOnly = true)
    public List<ScoredTarget> score(MatchingSource lost, double[][] vectors) {
        List<MatchingSource> targets = sources.targets(lost.getSpecies(), properties.getModelVersion());
        Map<Long, Notice> noticeMap = notices.findAllById(targets.stream().filter(t -> t.getKind() == SourceKind.NOTICE).map(MatchingSource::getSourceId).toList())
                .stream().collect(Collectors.toMap(Notice::getId, Function.identity()));
        List<ScoredTarget> result = new ArrayList<>();
        for (MatchingSource target : targets) {
            if (target.getOccurredOn() == null || lost.getOccurredOn() == null || target.getOccurredOn().isBefore(lost.getOccurredOn())
                    || !RegionPolicy.nearby(lost.getSidoCode(), target.getSidoCode())) continue;
            if (target.getKind() == SourceKind.NOTICE && !open(noticeMap.get(target.getSourceId()))) continue;
            SimilarityScorer.Score score = scorer.score(vectors, json.vectors(target.getVectorsJson()),
                    lost.getBreed(), target.getBreed(), lost.getColor(), target.getColor(), lost.getSigunguCode(), target.getSigunguCode());
            if (score.adjusted() >= properties.getThreshold()) result.add(new ScoredTarget(target.getId(), target.getSourceId(), target.getKind(), target.getGeneration(), score));
        }
        return result;
    }

    @Transactional
    public void completeTarget(Work work, double[][] vectors) {
        MatchingSource s = sources.lockById(work.source().getId()).orElseThrow();
        if (!s.owned(work.token(), work.generation())) return;
        s.complete(json.write(vectors), properties.getModelVersion());
        comparisonRequests.save(ComparisonRequest.create());
    }

    @Transactional
    public void completeLostPet(Work work, double[][] vectors, List<ScoredTarget> scored) {
        // Consistent lock order with F-08: LostPet first, its matching source second.
        LostPet pet = lostPets.lockById(work.source().getSourceId()).orElse(null);
        MatchingSource s = sources.lockById(work.source().getId()).orElseThrow();
        if (!s.owned(work.token(), work.generation())) return;
        if (pet == null || pet.getStatus() != LostPetStatus.SEARCHING) { s.cancel(); return; }
        Map<String, Match> existing = matches.findByLostPetId(pet.getId()).stream().collect(Collectors.toMap(
                m -> key(m.getType() == MatchType.NOTICE ? SourceKind.NOTICE : SourceKind.SIGHTING,
                        m.getType() == MatchType.NOTICE ? m.getNotice().getId() : m.getSightingId()), Function.identity()));
        existing.values().forEach(Match::exclude);
        Map<Long, MatchingSource> current = sources.findAllById(scored.stream().map(ScoredTarget::id).toList()).stream()
                .collect(Collectors.toMap(MatchingSource::getId, Function.identity()));
        Map<Long, Notice> currentNotices = notices.findAllById(scored.stream().filter(t -> t.kind() == SourceKind.NOTICE).map(ScoredTarget::sourceId).toList())
                .stream().collect(Collectors.toMap(Notice::getId, Function.identity()));
        for (ScoredTarget target : scored) {
            MatchingSource t = current.get(target.id());
            if (t == null || !t.isActive() || t.getGeneration() != target.generation() || t.getStatus() != ProcessingStatus.READY) continue;
            Notice notice = currentNotices.get(target.sourceId());
            if (target.kind() == SourceKind.NOTICE && !open(notice)) continue;
            String key = key(target.kind(), target.sourceId());
            Match match = existing.get(key);
            boolean fresh = match == null;
            if (fresh) match = target.kind() == SourceKind.NOTICE ? Match.ofNotice(pet, notice, 0) : Match.ofSighting(pet, target.sourceId(), 0);
            match.updateScore(target.score().adjusted() * 100, target.score().raw(), properties.getModelVersion());
            if (fresh) {
                matches.save(match);
                notifications.save(CandidateNotification.create(match.getId(), pet.getUser().getId(), pet.getId()));
            }
        }
        s.complete(json.write(vectors), properties.getModelVersion());
    }

    @Transactional
    public void fail(Work work, String code, boolean retryable) {
        sources.lockById(work.source().getId()).filter(s -> s.owned(work.token(), work.generation()))
                .ifPresent(s -> s.fail(code, properties.getMaxAttempts(), retryable));
    }

    static boolean open(Notice n) {
        return n != null && n.getStatus().isOpen() && (n.getNoticeEndDate() == null || !n.getNoticeEndDate().isBefore(LocalDate.now(ZoneId.of("Asia/Seoul"))));
    }
    private String key(SourceKind kind, Long id) { return kind.name() + ":" + id; }
    public record Work(MatchingSource source, String token, long generation) {}
    public record ScoredTarget(Long id, Long sourceId, SourceKind kind, long generation, SimilarityScorer.Score score) {}
}
