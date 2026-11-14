package com.pawpose.domain.match;

import com.pawpose.domain.lostpet.*;
import com.pawpose.domain.match.dto.CandidatePageResponse;
import com.pawpose.domain.match.dto.CandidatePageResponse.Candidate;
import com.pawpose.domain.match.pipeline.*;
import com.pawpose.global.error.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class CandidateQueryService {
    private final LostPetRepository pets;
    private final MatchRepository matches;
    private final MatchingSourceRepository sources;
    private final ObjectMapper mapper;

    @Transactional(readOnly = true)
    public CandidatePageResponse list(Long userId, Long petId, String sort, int page, int size) {
        if (page < 0 || size < 1 || size > 20 || !(sort.equals("similarity") || sort.equals("deadline"))) throw new BusinessException(ErrorCode.INVALID_INPUT);
        LostPet pet = pets.findById(petId).orElseThrow(() -> new BusinessException(ErrorCode.LOST_PET_NOT_FOUND));
        if (!pet.isOwnedBy(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        MatchingSource source = sources.findByKindAndSourceId(SourceKind.LOST_PET, petId).orElse(null);
        ProcessingStatus state = pet.getStatus() == LostPetStatus.FOUND ? ProcessingStatus.CANCELLED
                : source == null ? ProcessingStatus.NOT_REQUESTED : source.getStatus();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        // The product shows the first 20 candidates. Page/size operate within that window.
        var candidates = sort.equals("deadline") ? matches.visibleByDeadline(petId, today, PageRequest.of(0, 20))
                : matches.visibleBySimilarity(petId, today, PageRequest.of(0, 20));
        long offset = (long) page * size;
        List<Match> window = offset >= candidates.getNumberOfElements() ? List.of()
                : candidates.getContent().subList((int) offset, (int) Math.min(offset + size, candidates.getNumberOfElements()));
        Map<Long, MatchingSource> sightings = sources.findByKindAndSourceIdIn(SourceKind.SIGHTING,
                window.stream().filter(m -> m.getType() == MatchType.SIGHTING).map(Match::getSightingId).toList())
                .stream().collect(Collectors.toMap(MatchingSource::getSourceId, Function.identity()));
        List<Candidate> items = window.stream().map(m -> card(m, sightings.get(m.getSightingId()), today)).toList();
        String message = switch (state) {
            case PENDING, PROCESSING -> "사진 분석 중이에요";
            case FAILED -> "사진 분석에 실패했어요. 잠시 후 다시 비교할게요";
            case NOT_REQUESTED -> "매칭에 필요한 등록 정보를 기다리고 있어요";
            case CANCELLED -> "찾음 처리되어 새로운 비교를 중단했어요";
            case READY -> candidates.isEmpty() ? "아직 비슷한 공고가 없어요. 새 공고가 올라오면 알려드릴게요" : null;
        };
        return new CandidatePageResponse(items, page, size, Math.min(20, candidates.getTotalElements()), state, message,
                source == null ? null : source.getErrorCode(), state == ProcessingStatus.PENDING || state == ProcessingStatus.PROCESSING ? 10 : null, false);
    }

    private Candidate card(Match m, MatchingSource sighting, LocalDate today) {
        var n = m.getNotice();
        LocalDate deadline = n == null ? null : n.getNoticeEndDate();
        Long days = deadline == null ? null : Math.max(0, ChronoUnit.DAYS.between(today, deadline));
        String image = n != null ? n.getImageUrl() : firstImage(sighting);
        return new Candidate(m.getId(), m.getType(), n == null ? null : n.getId(), m.getSightingId(), image,
                Math.round(m.getSimilarity() * 10) / 10.0, n == null ? null : n.getCareName(),
                n != null ? n.getHappenPlace() : sighting == null ? null : sighting.getPlace(),
                n != null ? n.getHappenDate() : sighting == null ? null : sighting.getOccurredOn(), deadline, days,
                days != null && days <= 3, m.getFeedback());
    }
    private String firstImage(MatchingSource source) {
        if (source == null) return null;
        try { String[] images = mapper.readValue(source.getImagesJson(), String[].class); return images.length == 0 ? null : images[0]; }
        catch (Exception e) { return null; }
    }
}
