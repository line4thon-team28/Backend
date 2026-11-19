package com.pawpose.domain.sighting;

import com.pawpose.domain.sighting.dto.SightingCreateRequest;
import com.pawpose.domain.match.MatchRepository;
import com.pawpose.domain.match.pipeline.*;
import com.pawpose.domain.registration.RegistrationReferenceService;
import com.pawpose.global.error.*;
import com.pawpose.global.storage.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class SightingService {
    private final SightingRepository sightings;
    private final RegistrationReferenceService references;
    private final PhotoUploadService uploads;
    private final MatchingInputService matching;
    private final MatchingSourceRepository sources;
    private final MatchRepository matches;
    private final TransactionTemplate transactions;
    private static final ZoneId SEOUL=ZoneId.of("Asia/Seoul");

    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public Result create(SightingCreateRequest form,List<MultipartFile> photos) {
        if(form.getSeenAt()==null||form.getSeenAt().isAfter(LocalDateTime.now(SEOUL)))
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        var region=references.resolve(form.getSido(),form.getSigungu());
        List<StoredPhoto> saved=uploads.upload(photos,3);
        try {
            return transactions.execute(tx->{
                Sighting s=sightings.save(Sighting.create(form,region,saved.stream().map(StoredPhoto::url).toList()));
                String place=String.join(" ",region.sidoName(),region.sigungu(),
                        form.getPlace()==null?"":form.getPlace()).trim();
                matching.registerSighting(s.getId(),s.getSpecies(),new MatchingProfile(s.getPhotos(),
                        s.getSeenAt().toLocalDate(),s.getSido(),s.getSigunguCode(),s.getBreed(),s.getColor(),place));
                return new Result(s.getId(),0,ProcessingStatus.PENDING,"사진 분석 중이에요",null,10);
            });
        } catch(RuntimeException e) { uploads.cleanup(saved); throw e; }
    }

    @Transactional(readOnly=true)
    public Result result(Long id) {
        Sighting sighting=sightings.findById(id).orElseThrow(()->new BusinessException(ErrorCode.SIGHTING_NOT_FOUND));
        MatchingSource source=sources.findByKindAndSourceId(SourceKind.SIGHTING,id).orElse(null);
        ProcessingStatus status=source==null?ProcessingStatus.NOT_REQUESTED:source.getStatus();
        String failure=source==null?null:source.getErrorCode();
        if(source!=null&&status==ProcessingStatus.READY) {
            var progress=sources.progress(source.getId()).orElseThrow();
            status=progress.status(); failure=progress.errorCode();
            if(status==ProcessingStatus.READY) {
                var regions=RegionPolicy.matchingRegions(sighting.getSido());
                long pending=sources.unfinishedLostPets(sighting.getSpecies(),sighting.getSeenAt().toLocalDate(),
                        regions,List.of(ProcessingStatus.PENDING,ProcessingStatus.PROCESSING));
                long failed=sources.unfinishedLostPets(sighting.getSpecies(),sighting.getSeenAt().toLocalDate(),
                        regions,List.of(ProcessingStatus.FAILED));
                if(progress.comparisonQueued()||pending>0) status=ProcessingStatus.PROCESSING;
                else if(failed>0) status=ProcessingStatus.PARTIAL;
            }
        }
        long count=source!=null&&source.getStatus()==ProcessingStatus.READY
                ?matches.countVisibleSightingMatches(id,source.getModelVersion()):0;
        String message=switch(status) {
            case PENDING,PROCESSING -> "사진 분석 및 실종 등록 비교 중이에요";
            case READY -> count==0?"아직 비슷한 실종 등록이 없어요":"비슷한 실종 등록을 찾았어요";
            case FAILED -> "사진 분석에 실패했어요. 잠시 후 다시 확인해주세요";
            case PARTIAL -> "일부 사진을 분석하지 못했어요. 확인된 결과부터 보여드릴게요";
            case CANCELLED -> "비교가 중단되었어요";
            case NOT_REQUESTED -> "사진 분석을 준비하고 있어요";
        };
        return new Result(id,count,status,message,failure,
                status==ProcessingStatus.PENDING||status==ProcessingStatus.PROCESSING?10:null);
    }
    public record Result(Long sightingId,long matchedCount,ProcessingStatus processingStatus,
                         String processingMessage,String failureCode,Integer retryAfterSeconds) {}
}
