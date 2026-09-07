package com.behcm.global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // Auth
    EMAIL_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "이미 사용 중인 이메일입니다."),
    NICKNAME_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "이미 사용 중인 닉네임입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    INVALID_OAUTH_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 로그인 제공자입니다."),

    // User
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "이메일 인증이 필요합니다."),
    EMAIL_SEND_FAILED(HttpStatus.BAD_REQUEST, "이메일 발송에 실패했습니다."),
    VERIFICATION_CODE_NOT_FOUND(HttpStatus.NOT_FOUND, "인증 코드를 찾을 수 없습니다."),
    VERIFICATION_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "인증 코드가 만료되었습니다."),
    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "잘못된 인증 코드입니다."),

    // Workout Room
    WORKOUT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "운동방을 찾을 수 없습니다."),
    WORKOUT_ROOM_FULL(HttpStatus.BAD_REQUEST, "운동방이 가득 찼습니다."),
    ALREADY_JOINED_WORKOUT_ROOM(HttpStatus.BAD_REQUEST, "이미 다른 운동방에 참여 중입니다."),
    ALREADY_JOINED_THIS_WORKOUT_ROOM(HttpStatus.CONFLICT, "이미 동일한 운동방에 참여 중입니다."),
    NOT_WORKOUT_ROOM_MEMBER(HttpStatus.FORBIDDEN, "운동방 멤버가 아닙니다."),
    NOT_WORKOUT_ROOM_OWNER(HttpStatus.FORBIDDEN, "방장이 아닙니다."),
    INVALID_ENTRY_CODE(HttpStatus.BAD_REQUEST, "입장코드가 맞지 않습니다."),
    WORKOUT_ROOM_ALREADY_STARTED(HttpStatus.BAD_REQUEST, "이미 시작된 운동방에는 참여할 수 없습니다."),
    INVALID_PENALTY_EFFECTIVE_DATE(HttpStatus.BAD_REQUEST, "전환 예정일은 오늘로부터 최소 7일 이후의 월요일이어야 합니다."),

    // Workout
    WORKOUT_ALREADY_UPLOADED(HttpStatus.BAD_REQUEST, "오늘 이미 운동을 인증했습니다."),
    WORKOUT_ALREADY_AUTHENTICATED(HttpStatus.BAD_REQUEST, "해당 날짜에 이미 운동 인증을 완료했습니다."),
    WORKOUT_NOT_FOUND(HttpStatus.NOT_FOUND, "운동 기록을 찾을 수 없습니다."),
    WORKOUT_RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "운동 기록을 찾을 수 없습니다."),
    CANNOT_DELETE_WORKOUT(HttpStatus.BAD_REQUEST, "당일 운동만 삭제할 수 있습니다."),

    // Social (리액션 / 댓글)
    UNSUPPORTED_REACTION(HttpStatus.BAD_REQUEST, "지원하지 않는 리액션입니다."),
    REACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "리액션을 누른 적이 없습니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."),
    NOT_COMMENT_AUTHOR(HttpStatus.FORBIDDEN, "본인이 작성한 댓글만 삭제할 수 있습니다."),

    // Chat
    CHAT_MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "채팅 메시지를 찾을 수 없습니다."),

    // File
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 업로드에 실패했습니다."),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다."),
    INVALID_FILE(HttpStatus.BAD_REQUEST, "유효하지 않은 파일입니다."),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "파일 크기가 너무 큽니다."),

    // Penalty
    PAYMENT_FAILED(HttpStatus.BAD_REQUEST, "결제 처리에 실패했습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),

    // Rest
    REST_PERIOD_OVERLAP(HttpStatus.BAD_REQUEST, "이미 등록된 휴식일이 있습니다. 제외 후 재등록해주세요."),

    // Toss Stock
    TOSS_ACCOUNT_NOT_CONFIGURED(HttpStatus.NOT_FOUND, "연동되지 않은 토스증권 계좌입니다."),
    TOSS_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "토스증권 계좌를 찾을 수 없습니다."),
    TOSS_TOKEN_ISSUE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "토스증권 인증에 실패했습니다."),
    TOSS_UNAUTHORIZED(HttpStatus.INTERNAL_SERVER_ERROR, "토스증권 API 인증이 거부되었습니다."),
    TOSS_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "토스증권 API 요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요."),
    TOSS_API_FAILED(HttpStatus.BAD_GATEWAY, "토스증권 API 호출에 실패했습니다."),

    // Toss Stock - 종목
    TOSS_STOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "종목을 찾을 수 없습니다."),
    TOSS_STOCK_UNIVERSE_NOT_READY(HttpStatus.SERVICE_UNAVAILABLE, "종목 목록을 준비 중입니다. 잠시 후 다시 시도해주세요."),

    // Toss Stock - 주문
    // 토스가 주는 원문 메시지는 그대로 노출하지 않는다 — 문구가 언제든 바뀌어 테스트가 흔들리고
    // 내부 용어가 샐 수 있다. code/message/requestId 는 로그로만 남기고 여기 문구로 치환한다.
    TOSS_ORDER_INVALID(HttpStatus.BAD_REQUEST, "주문 정보가 올바르지 않습니다."),
    TOSS_ORDER_LOC_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "LOC 주문은 미국 주식만 가능합니다."),
    TOSS_ORDER_HIGH_VALUE_CONFIRM_REQUIRED(HttpStatus.BAD_REQUEST, "1억원 이상 주문은 금액 확인이 필요합니다."),
    TOSS_ORDER_IN_PROGRESS(HttpStatus.CONFLICT, "이미 처리 중인 주문입니다. 미체결 주문을 확인해주세요."),
    TOSS_ORDER_IDEMPOTENCY_CONFLICT(HttpStatus.UNPROCESSABLE_ENTITY, "주문 내용이 변경되었습니다. 다시 시도해주세요."),
    TOSS_ORDER_INSUFFICIENT_BUYING_POWER(HttpStatus.UNPROCESSABLE_ENTITY, "주문 가능 금액이 부족합니다."),
    TOSS_ORDER_HOURS_CLOSED(HttpStatus.UNPROCESSABLE_ENTITY, "지금은 주문할 수 있는 시간이 아닙니다."),
    TOSS_ORDER_PRICE_OUT_OF_RANGE(HttpStatus.UNPROCESSABLE_ENTITY, "주문 가격이 상·하한가를 벗어났습니다."),
    TOSS_ORDER_STOCK_RESTRICTED(HttpStatus.UNPROCESSABLE_ENTITY, "거래가 제한된 종목입니다."),
    TOSS_ORDER_TYPE_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "이 종목에는 사용할 수 없는 주문 유형입니다."),
    TOSS_ORDER_OPPOSITE_PENDING(HttpStatus.UNPROCESSABLE_ENTITY, "같은 종목에 반대 방향의 미체결 주문이 있습니다."),
    TOSS_ORDER_MAX_AMOUNT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "1회 주문 한도를 초과했습니다."),
    TOSS_ORDER_ACCOUNT_RESTRICTED(HttpStatus.UNPROCESSABLE_ENTITY, "계좌 상태로 인해 주문할 수 없습니다."),
    TOSS_ORDER_PREREQUISITE_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "토스증권 앱에서 사전 동의·교육 이수가 필요합니다."),
    TOSS_ORDER_REJECTED(HttpStatus.UNPROCESSABLE_ENTITY, "주문이 거부되었습니다."),
    TOSS_ORDER_NOT_CANCELABLE(HttpStatus.UNPROCESSABLE_ENTITY, "취소할 수 없는 주문입니다."),
    TOSS_MAINTENANCE(HttpStatus.SERVICE_UNAVAILABLE, "토스증권이 점검 중입니다. 잠시 후 다시 시도해주세요."),

    // Common
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 입력값입니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근이 거부되었습니다."),
    COUNT_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "허용된 개수 제한을 초과했습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
