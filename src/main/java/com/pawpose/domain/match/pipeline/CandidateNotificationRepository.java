package com.pawpose.domain.match.pipeline;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface CandidateNotificationRepository extends JpaRepository<CandidateNotification, Long> {
    List<CandidateNotification> findByDeliveredFalseOrderById(Pageable page);
}
