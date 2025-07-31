package com.behcm.domain.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ChatHistoryResponse {
    private List<ChatMessageResponse> messages;
    private Long nextCursorId; // 다음에 요청할 커서 ID
    private boolean hasNext; // 다음 페이지 존재 여부
}
