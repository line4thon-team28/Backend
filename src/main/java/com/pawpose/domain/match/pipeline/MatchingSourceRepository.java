package com.pawpose.domain.match.pipeline;

import com.pawpose.domain.common.Species;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MatchingSourceRepository extends JpaRepository<MatchingSource, Long> {
    @Query("select count(s) from MatchingSource s where s.kind = com.pawpose.domain.match.pipeline.SourceKind.LOST_PET "
            + "and s.active = true and s.species = :species and s.occurredOn <= :date "
            + "and s.sidoCode in :regions and s.status in :statuses")
    long unfinishedLostPets(@Param("species") Species species, @Param("date") java.time.LocalDate date,
                            @Param("regions") java.util.Set<String> regions,
                            @Param("statuses") List<ProcessingStatus> statuses);
    Optional<MatchingSource> findByKindAndSourceId(SourceKind kind, Long sourceId);
    @Query("select s.id from MatchingSource s where s.kind = :kind and s.sourceId = :sourceId")
    Optional<Long> findSourceId(@Param("kind") SourceKind kind, @Param("sourceId") Long sourceId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MatchingSource s where s.id = :id")
    Optional<MatchingSource> lockById(@Param("id") Long id);
    @Query("select new com.pawpose.domain.match.pipeline.MatchingProgress(s.status, s.errorCode, case when exists (select r.id from ComparisonRequest r) then true else false end) from MatchingSource s where s.id = :id")
    Optional<MatchingProgress> progress(@Param("id") Long id);
    @Query("select s.id from MatchingSource s where s.active = true and s.availableAt <= :now and ((s.status = com.pawpose.domain.match.pipeline.ProcessingStatus.PENDING or (s.status = com.pawpose.domain.match.pipeline.ProcessingStatus.FAILED and s.automaticRetry = true)) or (s.status = com.pawpose.domain.match.pipeline.ProcessingStatus.PROCESSING and s.leaseUntil <= :now)) order by case when s.kind = com.pawpose.domain.match.pipeline.SourceKind.LOST_PET then 0 when s.kind = com.pawpose.domain.match.pipeline.SourceKind.SIGHTING then 1 else 2 end, s.availableAt, s.id")
    List<Long> workIds(@Param("now") Instant now, Pageable page);
    @Query("select s from MatchingSource s where s.kind <> com.pawpose.domain.match.pipeline.SourceKind.LOST_PET and s.active = true and s.status = com.pawpose.domain.match.pipeline.ProcessingStatus.READY and s.species = :species and s.modelVersion = :version")
    List<MatchingSource> targets(@Param("species") Species species, @Param("version") String version);
    @Query("select s.id from MatchingSource s where s.kind = :kind and s.active = true order by s.id")
    List<Long> activeSourceIds(@Param("kind") SourceKind kind);
    List<MatchingSource> findByKindAndSourceIdIn(SourceKind kind, List<Long> ids);
    @Query("select s.id from MatchingSource s where s.active = true and s.status = com.pawpose.domain.match.pipeline.ProcessingStatus.READY and s.modelVersion <> :version order by s.id")
    List<Long> outdatedSourceIds(@Param("version") String version);
    @Query("select count(s) from MatchingSource s where s.kind <> com.pawpose.domain.match.pipeline.SourceKind.LOST_PET and s.active = true and s.species = :species and s.occurredOn >= :date and s.sidoCode in :regions and s.status in :statuses and (s.kind = com.pawpose.domain.match.pipeline.SourceKind.SIGHTING or exists (select n.id from Notice n where n.id = s.sourceId and n.status = com.pawpose.domain.notice.NoticeStatus.NOTICE and (n.noticeEndDate is null or n.noticeEndDate >= :today)))")
    long unfinishedTargets(@Param("species") Species species, @Param("date") java.time.LocalDate date,
                           @Param("regions") java.util.Set<String> regions, @Param("statuses") List<ProcessingStatus> statuses,
                           @Param("today") java.time.LocalDate today);
}
