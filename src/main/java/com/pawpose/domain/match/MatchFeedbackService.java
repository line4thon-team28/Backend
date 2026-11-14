package com.pawpose.domain.match;

import com.pawpose.domain.match.dto.FeedbackResponse;
import com.pawpose.global.error.BusinessException;
import com.pawpose.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MatchFeedbackService {

    private final MatchRepository matchRepository;

    @Transactional
    public FeedbackResponse giveFeedback(Long userId, Long matchId, MatchFeedback result) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCH_NOT_FOUND));
        if (!match.getLostPet().isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        match.applyFeedback(result);
        return FeedbackResponse.from(match);
    }
}
