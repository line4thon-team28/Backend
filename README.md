# 포포즈 (PAWPOSE) Backend

F-03·F-04 실행·Swagger·팀 연동 규격: [공고 수집과 이미지 매칭](docs/f03-f04.md)

실종 반려동물 찾기 서비스 · 멋사 4호선톤 8팀

## 실행

1. JDK 17 이상 설치 (IntelliJ: File > Project Structure > SDK)
2. `.env.example` 을 복사해 `.env` 를 만들고 값 채우기 (값은 디스코드 비공개 채널)
3. `./gradlew bootRun` (윈도우: `gradlew.bat bootRun`) 또는 IntelliJ 에서 `PawposeApplication` 실행
4. Swagger: http://localhost:8080/swagger-ui.html

기본 프로필은 `local` 이고 H2 메모리 DB 를 씁니다. 서버를 켤 때마다 테스트 데이터가 다시 들어갑니다.

- 로그인: `demo@pawpose.com` / `password1234`
- 공고 1 = D-7, 공고 2 = D-2(긴급), 공고 3 = 반환 종료
- match 1, 2 = demo 계정 후보 (피드백 테스트용)

Swagger 에서 로그인 → 응답의 `accessToken` 복사 → 우측 상단 **Authorize** 에 붙여넣기.

## 구조

```
com.pawpose
├─ global              공통 (모두 같이 씀, 고칠 땐 단톡에 공유)
│  ├─ common           ApiResponse, BaseTimeEntity
│  ├─ error            ErrorCode, BusinessException, GlobalExceptionHandler
│  ├─ security         JWT, SecurityConfig
│  └─ config           Swagger
└─ domain              기능별 패키지 (한 사람이 한 패키지)
   ├─ user, auth       F-01 + 카카오 로그인
   ├─ notice           F-05 상세 조회 / F-03 수집(BE-1)
   ├─ match            F-07 피드백 / F-04 매칭(BE-2)
   ├─ lostpet          F-02, F-08 (현재 최소 필드만 있음)
   └─ common           Species, Sex 공용 enum
```

## 공통 규칙 (명세서 6장)

- 경로는 `/api/v1/...`
- 성공 `{ "data": ..., "message": null }` / 실패 `{ "data": null, "message": "화면 문구", "code": "ERROR_CODE" }`
- 실패는 `throw new BusinessException(ErrorCode.XXX)` 한 줄이면 위 형식으로 나갑니다. 새 에러는 `ErrorCode` 에 추가
- 로그인한 사용자 id: 컨트롤러 파라미터에 `@AuthenticationPrincipal Long userId`
- 비로그인 허용 경로는 `SecurityConfig` 에서 관리 (추가 필요하면 말해주세요)
- 토큰 만료 시 401 + `code: TOKEN_EXPIRED` → FE 는 로그인 화면으로

## 카카오 로그인 (FE 연동)

1. FE 가 아래 주소로 이동
   `https://kauth.kakao.com/oauth/authorize?client_id={REST_API_KEY}&redirect_uri={REDIRECT_URI}&response_type=code`
2. 카카오가 `{REDIRECT_URI}?code=xxx` 로 돌려보냄
3. FE 가 `POST /api/v1/auth/kakao { "code": "xxx" }` 호출 → `accessToken` 받음

`REDIRECT_URI` 는 FE 주소이고, 카카오 디벨로퍼스 등록값 · FE 요청값 · 백엔드 `.env` 의 `KAKAO_REDIRECT_URI` 세 개가 **완전히 같아야** 합니다. 코드는 한 번만 쓸 수 있습니다.

## 브랜치

- `main`: 배포용, 직접 push 금지
- `feat/F-01-auth` 처럼 기능 ID 로 브랜치 → PR → 1명 리뷰 후 머지
