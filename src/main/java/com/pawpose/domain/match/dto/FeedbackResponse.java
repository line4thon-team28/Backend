package com.pawpose.domain.match.dto;

import com.pawpose.domain.match.Match;
import com.pawpose.domain.match.MatchFeedback;

public record FeedbackResponse(Long matchId, MatchFeedback feedback) {

    public static FeedbackResponse from(Match match) {
        return new FeedbackResponse(match.getId(), match.getFeedback());
    }
}
