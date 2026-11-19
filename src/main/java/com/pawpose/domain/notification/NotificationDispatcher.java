package com.pawpose.domain.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매칭 outbox 를 주기적으로 읽어 알림을 만듭니다. (기본 5초마다)
 * pawpose.notification.enabled=false 면 멈춥니다. (테스트에서 직접 호출할 때 사용)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final NotificationService notificationService;

    @Value("${pawpose.notification.enabled:true}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${pawpose.notification.poll-ms:5000}",
            initialDelayString = "${pawpose.notification.initial-delay-ms:10000}")
    public void poll() {
        if (!enabled) return;
        try {
            int created = notificationService.deliverPending();
            if (created > 0) log.info("Created {} candidate notifications", created);
        } catch (Exception e) {
            // outbox 조회 자체가 실패한 경우. 건별 실패는 NotificationService 에서 처리
            log.warn("Candidate notification delivery failed; will retry next poll", e);
        }
    }
}
