package com.pawpose.domain.match.pipeline;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ComparisonRequestRepository extends JpaRepository<ComparisonRequest, Long> {
    @Query("select r.id from ComparisonRequest r order by r.id")
    List<Long> pendingIds(Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ComparisonRequest r where r.id = :id")
    Optional<ComparisonRequest> lockById(@Param("id") Long id);
}
