package com.pawpose.domain.notification.dto;

import com.pawpose.domain.match.MatchType;
import com.pawpose.domain.notification.Notification;

import java.time.LocalDateTime;

/** S-04 알림 카드. 클릭하면 lostPetId 의 S-03(매칭 결과)으로 이동합니다. */
public record NotificationResponse(Long notificationId, Long lostPetId, Long matchId, MatchType matchType,
                                   double similarity, String message, boolean read,
                                   LocalDateTime createdAt, LocalDateTime readAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getLostPetId(), n.getMatchId(), n.getMatchType(),
                n.getSimilarity(), n.getMessage(), n.isRead(), n.getCreatedAt(), n.getReadAt());
    }
}
