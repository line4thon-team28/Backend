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
            // 실패한 묶음은 트랜잭션이 롤백되어 다음 주기에 다시 처리됩니다
            log.warn("Candidate notification delivery failed; will retry next poll", e);
        }
    }
}
