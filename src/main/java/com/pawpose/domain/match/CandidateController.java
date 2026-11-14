package com.pawpose.domain.match;

import com.pawpose.domain.match.dto.CandidatePageResponse;
import com.pawpose.domain.match.pipeline.MatchingInputService;
import com.pawpose.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "F-04 이미지 매칭 후보")
@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/lost-pets/{lostPetId}/matches")
public class CandidateController {
    private final CandidateQueryService queries;
    private final MatchingInputService inputs;

    @Operation(summary = "내 실종 등록의 후보 목록", description = "sort=similarity 또는 deadline. YES 우선, NO 제외. 마감일 없는 목격 후보는 마감임박순에서 뒤에 배치합니다. 상위 20건 안에서 page(0부터), size(1~20)를 적용합니다. 점수는 동일 동물일 확률이 아닙니다. PENDING/PROCESSING이면 10초 후 다시 조회합니다.")
    @GetMapping
    public ApiResponse<CandidatePageResponse> list(@AuthenticationPrincipal Long userId, @PathVariable Long lostPetId,
            @RequestParam(defaultValue = "similarity") String sort, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(queries.list(userId, lostPetId, sort, page, size));
    }

    @Operation(summary = "실패한 사진 분석 재시도", description = "본인 등록의 FAILED 작업만 대기 상태로 바꿉니다. 진행 중인 작업을 중복 생성하지 않습니다.")
    @PostMapping("/retry") @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Void> retry(@AuthenticationPrincipal Long userId, @PathVariable Long lostPetId) {
        inputs.retry(userId, lostPetId); return ApiResponse.ok();
    }
}
