package com.pawpose.domain.match;

import com.pawpose.domain.match.dto.FeedbackRequest;
import com.pawpose.domain.match.dto.FeedbackResponse;
import com.pawpose.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "F-07 매칭 피드백")
@RestController
@RequestMapping("/api/v1/matches")
@RequiredArgsConstructor
public class MatchFeedbackController {

    private final MatchFeedbackService matchFeedbackService;

    @Operation(summary = "맞아요 / 아니에요",
            description = "yes: 목록 맨 위 고정, no: 목록에서 숨김, none: 되돌리기. 내 실종 등록의 후보만 가능(아니면 403).")
    @PostMapping("/{matchId}/feedback")
    public ApiResponse<FeedbackResponse> feedback(@AuthenticationPrincipal Long userId,
                                                  @PathVariable Long matchId,
                                                  @Valid @RequestBody FeedbackRequest request) {
        return ApiResponse.ok(matchFeedbackService.giveFeedback(userId, matchId, request.result()));
    }
}
