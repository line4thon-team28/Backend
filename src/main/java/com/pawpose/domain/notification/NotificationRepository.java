package com.pawpose.domain.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 최신순 */
    Page<Notification> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadFalse(Long userId);

    boolean existsByMatchId(Long matchId);

    @Modifying(clearAutomatically = true)
    /** 한 번에 업데이트하므로 JPA 자동 시간 갱신이 안 됨 → updatedAt 도 직접 갱신 */
    @Query("update Notification n set n.read = true, n.readAt = :now, n.updatedAt = :now where n.userId = :userId and n.read = false")
    int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
