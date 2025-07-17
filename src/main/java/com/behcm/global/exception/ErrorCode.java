package com.behcm.global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // Auth
    EMAIL_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "이미 존재하는 이메일입니다."),
    NICKNAME_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "이미 존재하는 닉네임입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),

    // User
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "이메일 인증이 필요합니다."),

    // Workout Room
    WORKOUT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "운동방을 찾을 수 없습니다."),
    WORKOUT_ROOM_FULL(HttpStatus.BAD_REQUEST, "운동방이 가득 찼습니다."),
    ALREADY_JOINED_WORKOUT_ROOM(HttpStatus.BAD_REQUEST, "이미 다른 운동방에 참여 중입니다."),
    NOT_WORKOUT_ROOM_MEMBER(HttpStatus.FORBIDDEN, "운동방 멤버가 아닙니다."),
    NOT_WORKOUT_ROOM_OWNER(HttpStatus.FORBIDDEN, "방장이 아닙니다."),
    INVALID_ENTRY_CODE(HttpStatus.BAD_REQUEST, "입장코드가 맞지 않습니다."),
    WORKOUT_ROOM_ALREADY_STARTED(HttpStatus.BAD_REQUEST, "이미 시작된 운동방에는 참여할 수 없습니다."),

    // Workout
    WORKOUT_ALREADY_UPLOADED(HttpStatus.BAD_REQUEST, "오늘 이미 운동을 인증했습니다."),
    WORKOUT_NOT_FOUND(HttpStatus.NOT_FOUND, "운동 기록을 찾을 수 없습니다."),
    CANNOT_DELETE_WORKOUT(HttpStatus.BAD_REQUEST, "당일 운동만 삭제할 수 있습니다."),

    // File
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 업로드에 실패했습니다."),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다."),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "파일 크기가 너무 큽니다."),

    // Common
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 입력값입니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근이 거부되었습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
