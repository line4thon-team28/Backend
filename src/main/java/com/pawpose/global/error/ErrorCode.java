package com.pawpose.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 에러 코드는 여기 한 곳에 모아 둡니다. 새 도메인 담당자도 여기에 추가하면 됩니다.
 * message 는 FE 가 그대로 화면에 띄우는 문구입니다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값을 확인해주세요"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "로그인이 만료됐어요. 다시 로그인해주세요"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없어요"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "잠시 후 다시 시도해주세요"),

    // auth (F-01)
    EMAIL_DUPLICATED(HttpStatus.BAD_REQUEST, "이미 가입된 이메일이에요"),
    LOGIN_FAILED(HttpStatus.BAD_REQUEST, "이메일 또는 비밀번호를 확인해주세요"),
    KAKAO_LOGIN_FAILED(HttpStatus.BAD_REQUEST, "카카오 로그인에 실패했어요. 다시 시도해주세요"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없어요"),

    // notice (F-05)
    NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "공고를 찾을 수 없어요"),

    // match (F-07)
    MATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "후보를 찾을 수 없어요"),

    // lost-pet
    INVALID_PHOTO_COUNT(HttpStatus.BAD_REQUEST, "실종 사진은 1~5장, 목격 사진은 1~3장을 올려주세요"),
    INVALID_IMAGE(HttpStatus.BAD_REQUEST, "정상적인 JPEG 또는 PNG 사진을 올려주세요"),
    UNSUPPORTED_IMAGE(HttpStatus.BAD_REQUEST, "JPEG와 PNG 형식만 지원해요"),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "사진은 장당 10MB, 요청 전체는 50MB 이하여야 해요"),
    PHOTO_UPLOAD_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "사진 저장에 실패했어요. 잠시 후 다시 시도해주세요"),
    REFERENCE_DATA_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "지역 정보를 가져오지 못했어요. 잠시 후 다시 시도해주세요"),
    SIGHTING_NOT_FOUND(HttpStatus.NOT_FOUND, "목격 제보를 찾을 수 없어요"),
    LOST_PET_NOT_FOUND(HttpStatus.NOT_FOUND, "실종 등록을 찾을 수 없어요"),
    MATCHING_NOT_REGISTERED(HttpStatus.CONFLICT, "매칭에 필요한 등록 정보가 아직 없어요"),
    MATCHING_STOPPED(HttpStatus.CONFLICT, "찾음 처리된 등록은 비교를 중단했어요");

    private final HttpStatus status;
    private final String message;
}
