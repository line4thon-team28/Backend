package com.pawpose.domain.match.pipeline;

import com.pawpose.domain.common.Species;
import com.pawpose.domain.lostpet.*;
import com.pawpose.domain.notice.*;
import com.pawpose.domain.notice.collection.NoticeCollectedEvent;
import com.pawpose.global.error.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service @RequiredArgsConstructor
public class MatchingInputService {
    private final MatchingSourceRepository sources;
    private final LostPetRepository lostPets;
    private final NoticeRepository notices;
    private final MatchingJson json;

    /** Call within the registration transaction after saving LostPet. URLs must be server-issued storage URLs. */
    @Transactional
    public void registerLostPet(Long lostPetId, MatchingProfile profile) {
        LostPet pet = lostPets.lockById(lostPetId).orElseThrow(() -> new BusinessException(ErrorCode.LOST_PET_NOT_FOUND));
        validate(profile, 5);
        register(SourceKind.LOST_PET, pet.getId(), pet.getSpecies(), profile, pet.getStatus() == LostPetStatus.SEARCHING);
    }

    /** F-06 owns the Sighting record and calls this service in its save transaction. */
    @Transactional
    public void registerSighting(Long sightingId, Species species, MatchingProfile profile) {
        if (sightingId == null || sightingId < 1 || species == null) throw new BusinessException(ErrorCode.INVALID_INPUT);
        validate(profile, 3);
        register(SourceKind.SIGHTING, sightingId, species, profile, true);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void noticeCollected(NoticeCollectedEvent event) {
        Notice n = notices.findById(event.noticeId()).orElseThrow();
        boolean active = n.getStatus().isOpen() && n.getImageUrl() != null && !n.getImageUrl().isBlank()
                && (n.getNoticeEndDate() == null || !n.getNoticeEndDate().isBefore(LocalDate.now(ZoneId.of("Asia/Seoul"))));
        register(SourceKind.NOTICE, n.getId(), n.getSpecies(), new MatchingProfile(
                n.getImageUrl() == null ? List.of() : List.of(n.getImageUrl()),
                n.getNoticeStartDate() != null ? n.getNoticeStartDate() : n.getHappenDate(),
                n.getSidoCode(), n.getSigunguCode(), n.getBreed(), n.getColor(), n.getHappenPlace()), active);
        // Closing/changing a target also invalidates previously computed candidate eligibility.
        if (!active) enqueueLostPets();
    }

    @Transactional
    public void retry(Long userId, Long lostPetId) {
        LostPet pet = ownedLocked(userId, lostPetId);
        if (pet.getStatus() != LostPetStatus.SEARCHING) throw new BusinessException(ErrorCode.MATCHING_STOPPED);
        MatchingSource source = sources.findByKindAndSourceId(SourceKind.LOST_PET, lostPetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCHING_NOT_REGISTERED));
        source = sources.lockById(source.getId()).orElseThrow();
        if (source.getStatus() == ProcessingStatus.FAILED) source.enqueue();
    }

    /** F-08 should use this method so FOUND and candidate persistence share the same row lock. */
    @Transactional
    public void markFound(Long userId, Long lostPetId) {
        LostPet pet = ownedLocked(userId, lostPetId); pet.markFound();
        sources.findByKindAndSourceId(SourceKind.LOST_PET, lostPetId).ifPresent(s -> sources.lockById(s.getId()).orElseThrow().cancel());
    }

    private LostPet ownedLocked(Long userId, Long id) {
        LostPet pet = lostPets.lockById(id).orElseThrow(() -> new BusinessException(ErrorCode.LOST_PET_NOT_FOUND));
        if (!pet.isOwnedBy(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        return pet;
    }
    private void register(SourceKind kind, Long id, Species species, MatchingProfile profile, boolean active) {
        MatchingSource source = sources.findByKindAndSourceId(kind, id).map(s -> sources.lockById(s.getId()).orElseThrow())
                .orElseGet(() -> MatchingSource.create(kind, id));
        source.register(species, profile, json.write(profile.imageUrls()), active);
        sources.save(source);
    }
    void enqueueLostPets() {
        for (MatchingSource s : sources.findByKindAndActiveTrueOrderById(SourceKind.LOST_PET)) sources.lockById(s.getId()).orElseThrow().enqueue();
    }
    private void validate(MatchingProfile p, int max) {
        if (p == null || p.imageUrls().isEmpty() || p.imageUrls().size() > max || p.imageUrls().stream().anyMatch(u -> u == null || u.isBlank() || u.length() > 2048)
                || p.occurredOn() == null || p.occurredOn().isAfter(LocalDate.now(ZoneId.of("Asia/Seoul")))
                || RegionPolicy.canonical(p.sidoCode()) == null || tooLong(p.sigunguCode(), 100) || tooLong(p.breed(), 100) || tooLong(p.color(), 100) || tooLong(p.place(), 500))
            throw new BusinessException(ErrorCode.INVALID_INPUT);
    }
    private boolean tooLong(String s, int max) { return s != null && s.length() > max; }
}
