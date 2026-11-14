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
    Optional<MatchingSource> findByKindAndSourceId(SourceKind kind, Long sourceId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MatchingSource s where s.id = :id")
    Optional<MatchingSource> lockById(@Param("id") Long id);
    @Query("select s.id from MatchingSource s where s.active = true and s.availableAt <= :now and ((s.status = com.pawpose.domain.match.pipeline.ProcessingStatus.PENDING or (s.status = com.pawpose.domain.match.pipeline.ProcessingStatus.FAILED and s.automaticRetry = true)) or (s.status = com.pawpose.domain.match.pipeline.ProcessingStatus.PROCESSING and s.leaseUntil <= :now)) order by case when s.kind = com.pawpose.domain.match.pipeline.SourceKind.LOST_PET then 0 when s.kind = com.pawpose.domain.match.pipeline.SourceKind.SIGHTING then 1 else 2 end, s.availableAt, s.id")
    List<Long> workIds(@Param("now") Instant now, Pageable page);
    @Query("select s from MatchingSource s where s.kind <> com.pawpose.domain.match.pipeline.SourceKind.LOST_PET and s.active = true and s.status = com.pawpose.domain.match.pipeline.ProcessingStatus.READY and s.species = :species and s.modelVersion = :version")
    List<MatchingSource> targets(@Param("species") Species species, @Param("version") String version);
    List<MatchingSource> findByKindAndActiveTrueOrderById(SourceKind kind);
    List<MatchingSource> findByKindAndSourceIdIn(SourceKind kind, List<Long> ids);
    List<MatchingSource> findByActiveTrueAndStatusAndModelVersionNot(ProcessingStatus status, String version);
}
