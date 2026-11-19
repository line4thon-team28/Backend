package com.pawpose.domain.notification;

import com.pawpose.domain.match.Match;
import com.pawpose.domain.match.MatchType;
import com.pawpose.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * F-09 새 후보 알림 (S-04 알림 목록 · 헤더 뱃지).
 * 매칭(F-04)이 새 후보를 만들 때 남기는 outbox(matching_notification_outbox)를
 * NotificationService.deliverPending 이 읽어서 한 건씩 만듭니다.
 * match_id unique: 같은 후보로 알림이 두 번 생기지 않음.
 */
@Getter
@Entity
@Table(name = "notification",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_match", columnNames = "match_id"),
        indexes = @Index(name = "idx_notification_user", columnList = "user_id, id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 클릭 시 이동할 S-03(매칭 결과) 화면의 실종 등록 ID */
    @Column(name = "lost_pet_id", nullable = false)
    private Long lostPetId;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    /** 보호소 공고 / 목격 제보 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MatchType matchType;

    /** 알림이 만들어질 때의 유사도 0~100 (%) */
    @Column(nullable = false)
    private double similarity;

    @Column(nullable = false, length = 100)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    private LocalDateTime readAt;

    public static Notification newCandidate(Long userId, Long lostPetId, Match match) {
        Notification n = new Notification();
        n.userId = userId;
        n.lostPetId = lostPetId;
        n.matchId = match.getId();
        n.matchType = match.getType();
        n.similarity = match.getSimilarity();
        n.message = message(match.getType());
        n.read = false;
        return n;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public void markRead() {
        if (!read) {
            read = true;
            readAt = LocalDateTime.now();
        }
    }

    private static String message(MatchType type) {
        return type == MatchType.SIGHTING
                ? "우리 아이와 비슷한 아이를 봤다는 목격 제보가 있어요"
                : "보호소 공고에서 우리 아이와 비슷한 아이를 찾았어요";
    }
}
