package com.pawpose.domain.notice;

import com.pawpose.domain.notice.dto.NoticeDetailResponse;
import com.pawpose.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "F-05 공고 상세")
@RestController
@RequestMapping("/api/v1/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @Operation(summary = "공고 상세 + 마감 카운트다운", description = "비로그인 허용")
    @GetMapping("/{noticeId}")
    public ApiResponse<NoticeDetailResponse> getDetail(@PathVariable Long noticeId) {
        return ApiResponse.ok(noticeService.getDetail(noticeId));
    }
}
