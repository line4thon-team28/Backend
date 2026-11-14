package com.pawpose.domain.notice.collection;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CollectionStateRepository extends JpaRepository<CollectionState, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CollectionState s where s.id = 1")
    Optional<CollectionState> lockState();
}
