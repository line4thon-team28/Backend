package com.pawpose.domain.match.dto;

import com.pawpose.domain.match.*;
import com.pawpose.domain.match.pipeline.ProcessingStatus;
import java.time.LocalDate;
import java.util.List;

public record CandidatePageResponse(List<Candidate> items, int page, int size, long totalCount,
                                    ProcessingStatus processingStatus, String processingMessage, String failureCode,
                                    Integer retryAfterSeconds, boolean scoreIsProbability) {
    public record Candidate(Long matchId, MatchType type, Long noticeId, Long sightingId, String imageUrl,
                            double similarity, String shelterName, String place, LocalDate occurredOn,
                            LocalDate noticeEndDate, Long daysLeft, boolean urgent, MatchFeedback feedback) {}
}
