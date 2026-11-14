package com.pawpose.domain.match.dto;

import com.pawpose.domain.match.MatchFeedback;
import jakarta.validation.constraints.NotNull;

/** { "result": "yes" | "no" | "none" } */
public record FeedbackRequest(
        @NotNull(message = "result 는 yes 또는 no 로 보내주세요") MatchFeedback result
) {
}
