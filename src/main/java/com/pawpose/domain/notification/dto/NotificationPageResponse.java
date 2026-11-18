package com.pawpose.domain.notification.dto;

import java.util.List;

public record NotificationPageResponse(List<NotificationResponse> items, int page, int size,
                                       long totalCount, boolean hasNext) {
}
