package com.pawpose.domain.match.dto;

import com.pawpose.domain.match.*;
import com.pawpose.domain.match.pipeline.ProcessingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

public record CandidatePageResponse(List<Candidate> items, int page, int size, long totalCount,
                                    ProcessingStatus processingStatus, String processingMessage, String failureCode,
                                    Integer retryAfterSeconds, boolean scoreIsProbability,
                                    long pendingTargetCount, long failedTargetCount) {
    public record Candidate(Long matchId, MatchType type, Long noticeId, Long sightingId, String imageUrl,
                            double similarity, String shelterName, String place, LocalDate occurredOn,
                            LocalDate noticeEndDate, Long daysLeft, boolean urgent, MatchFeedback feedback,
                            @Schema(description = "목격 제보자가 작성한 메모. 보호소 공고이거나 메모가 없으면 null입니다.", nullable = true)
                            String memo) {}
}
