package com.pawpose.domain.match.pipeline;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

/** F-09 consumes this row and marks it delivered in the same transaction as its notification insert. */
@Entity @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "matching_notification_outbox", uniqueConstraints = @UniqueConstraint(name = "uk_candidate_notification", columnNames = "match_id"))
public class CandidateNotification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "match_id", nullable = false) private Long matchId;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false) private Long lostPetId;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private boolean delivered;
    public static CandidateNotification create(Long matchId, Long userId, Long lostPetId) {
        CandidateNotification n = new CandidateNotification(); n.matchId = matchId; n.userId = userId;
        n.lostPetId = lostPetId; n.createdAt = Instant.now(); return n;
    }
    public void markDelivered() { delivered = true; }
}
