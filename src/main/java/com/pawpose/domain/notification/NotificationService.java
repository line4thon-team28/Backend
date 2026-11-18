package com.pawpose.domain.notification;

import com.pawpose.domain.lostpet.LostPet;
import com.pawpose.domain.lostpet.LostPetRepository;
import com.pawpose.domain.lostpet.LostPetStatus;
import com.pawpose.domain.match.Match;
import com.pawpose.domain.match.MatchRepository;
import com.pawpose.domain.match.pipeline.CandidateNotification;
import com.pawpose.domain.match.pipeline.CandidateNotificationRepository;
import com.pawpose.domain.notification.dto.NotificationPageResponse;
import com.pawpose.domain.notification.dto.NotificationResponse;
import com.pawpose.global.error.BusinessException;
import com.pawpose.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    static final int DELIVERY_BATCH_SIZE = 50;
    static final int MAX_PAGE_SIZE = 50;

    private final NotificationRepository notifications;
    private final CandidateNotificationRepository outbox;
    private final MatchRepository matches;
    private final LostPetRepository lostPets;

    /**
     * 매칭 outbox → 알림. 알림 저장과 outbox 처리 완료 표시를 같은 트랜잭션에서 합니다
     * (docs/f03-f04.md "F-09 연결"). 이미 알림이 있는 후보, 사라진 후보, 찾음 처리된 등록은
     * 알림 없이 처리 완료로 표시합니다.
     *
     * @return 새로 만든 알림 수
     */
    @Transactional
    public int deliverPending() {
        List<CandidateNotification> pending = outbox.findByDeliveredFalseOrderById(PageRequest.of(0, DELIVERY_BATCH_SIZE));
        int created = 0;
        for (CandidateNotification row : pending) {
            if (!notifications.existsByMatchId(row.getMatchId())) {
                Optional<Match> match = matches.findById(row.getMatchId());
                Optional<LostPet> pet = lostPets.findById(row.getLostPetId());
                if (match.isPresent() && pet.isPresent() && pet.get().getStatus() == LostPetStatus.SEARCHING) {
                    notifications.save(Notification.newCandidate(row.getUserId(), row.getLostPetId(), match.get()));
                    created++;
                }
            }
            row.markDelivered();
        }
        return created;
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse list(Long userId, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Page<Notification> result = notifications.findByUserIdOrderByIdDesc(userId, PageRequest.of(page, size));
        return new NotificationPageResponse(
                result.getContent().stream().map(NotificationResponse::from).toList(),
                page, size, result.getTotalElements(), result.hasNext());
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notifications.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public NotificationResponse markRead(Long userId, Long notificationId) {
        Notification notification = notifications.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        if (!notification.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        notification.markRead();
        return NotificationResponse.from(notification);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notifications.markAllRead(userId, LocalDateTime.now());
    }
}
