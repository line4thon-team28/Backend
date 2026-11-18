package com.pawpose.domain.notification;

import com.pawpose.domain.notification.dto.NotificationPageResponse;
import com.pawpose.domain.notification.dto.NotificationResponse;
import com.pawpose.domain.notification.dto.ReadAllResponse;
import com.pawpose.domain.notification.dto.UnreadCountResponse;
import com.pawpose.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "F-09 새 후보 알림")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "내 알림 목록 (S-04)",
            description = "최신순. page는 0부터, size는 1~50. 알림을 누르면 lostPetId로 S-03(매칭 결과) 화면으로 이동하고 읽음 처리 API를 호출합니다.")
    @GetMapping
    public ApiResponse<NotificationPageResponse> list(@AuthenticationPrincipal Long userId,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(notificationService.list(userId, page, size));
    }

    @Operation(summary = "안 읽은 알림 수 (헤더 뱃지)")
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> unreadCount(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(new UnreadCountResponse(notificationService.unreadCount(userId)));
    }

    @Operation(summary = "알림 읽음 처리", description = "내 알림만 가능(아니면 403). 이미 읽은 알림도 그대로 성공합니다.")
    @PatchMapping("/{notificationId}/read")
    public ApiResponse<NotificationResponse> read(@AuthenticationPrincipal Long userId,
                                                  @PathVariable Long notificationId) {
        return ApiResponse.ok(notificationService.markRead(userId, notificationId));
    }

    @Operation(summary = "모든 알림 읽음 처리", description = "읽음으로 바뀐 알림 수를 돌려줍니다.")
    @PatchMapping("/read-all")
    public ApiResponse<ReadAllResponse> readAll(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(new ReadAllResponse(notificationService.markAllRead(userId)));
    }
}
