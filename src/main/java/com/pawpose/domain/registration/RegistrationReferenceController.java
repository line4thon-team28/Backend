package com.pawpose.domain.registration;
import com.pawpose.domain.common.Species;
import com.pawpose.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Tag(name="F-02 등록 폼") @RestController @RequestMapping("/api/v1") @RequiredArgsConstructor
public class RegistrationReferenceController {
    private final RegistrationReferenceService references;
    @GetMapping("/regions")
    @Operation(summary="시도 목록 / 선택한 시도의 시군구",description="sido가 없으면 시도 목록. sido 지정 시 공공 API 시군구 목록. 키가 없으면 districtMode=MANUAL로 이름을 직접 입력합니다.")
    public ApiResponse<List<RegistrationReferenceService.Province>> regions(@RequestParam(required=false) String sido) {
        return ApiResponse.ok(references.regions(sido));
    }
    @GetMapping("/breeds")
    @Operation(summary="품종 입력 도움말",description="전체 공식 품종 목록이 아닌 제안 목록입니다. 별도 품종명을 직접 입력할 수 있습니다.")
    public ApiResponse<List<RegistrationReferenceService.Breed>> breeds(@RequestParam Species species) {
        return ApiResponse.ok(references.breeds(species));
    }
}
