package com.pawpose.domain.sighting;
import com.pawpose.domain.sighting.dto.SightingCreateRequest;
import com.pawpose.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@Tag(name="F-06 목격 제보")
@RestController @RequestMapping("/api/v1/sightings") @RequiredArgsConstructor
public class SightingController {
    private final SightingService service;
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary="비로그인 목격 제보",description="사진 1~3장, species 필수. seenAt은 한국 시간(예: 2026-11-19T09:30). 등록 직후 matchedCount=0은 분석 전 값입니다.")
    public ApiResponse<SightingService.Result> create(@Valid @ModelAttribute @ParameterObject SightingCreateRequest form,
            @RequestParam("photos") List<MultipartFile> photos) {
        return ApiResponse.ok(service.create(form,photos));
    }
    @GetMapping("/{id}")
    @Operation(summary="목격 제보 분석 진행 상태",description="PENDING/PROCESSING이면 retryAfterSeconds 후 다시 조회합니다.")
    public ApiResponse<SightingService.Result> result(@PathVariable Long id) {
        return ApiResponse.ok(service.result(id));
    }
}
