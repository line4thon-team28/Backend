package com.pawpose.domain.lostpet;
import com.pawpose.domain.lostpet.dto.*;
import com.pawpose.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@Tag(name="F-02 · F-08 실종 등록")
@RestController @RequestMapping("/api/v1/lost-pets") @RequiredArgsConstructor
public class LostPetController {
    private final LostPetService service;

    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary="실종 동물 등록",description="photos 1~5장. 사진은 실제 JPEG/PNG만 지원합니다. 매칭은 비동기 처리됩니다.")
    public ApiResponse<LostPetService.Created> create(@AuthenticationPrincipal Long userId,
            @Valid @ModelAttribute @ParameterObject LostPetCreateRequest form, @RequestParam("photos") List<MultipartFile> photos) {
        return ApiResponse.ok(service.create(userId,form,photos));
    }
    @GetMapping("/me")
    @Operation(summary="내 실종 등록 목록",description="최근 등록순. matchCount는 F-04와 같은 조건의 후보 수(최대 20)입니다.")
    public ApiResponse<List<LostPetResponse>> mine(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(service.mine(userId));
    }
    @GetMapping("/{id}")
    @Operation(summary="내 실종 등록 상세",description="본인이 등록한 동물만 조회할 수 있습니다.")
    public ApiResponse<LostPetResponse> detail(@AuthenticationPrincipal Long userId,@PathVariable Long id) {
        return ApiResponse.ok(service.detail(userId,id));
    }
    @PatchMapping("/{id}")
    @Operation(summary="찾음 완료 / 특징·연락처 수정",description="status는 FOUND만 허용. 생략/null은 유지, 빈 문자열은 선택 텍스트를 지웁니다.")
    public ApiResponse<LostPetResponse> update(@AuthenticationPrincipal Long userId,@PathVariable Long id,
            @Valid @RequestBody LostPetUpdateRequest request) {
        return ApiResponse.ok(service.update(userId,id,request));
    }
}
