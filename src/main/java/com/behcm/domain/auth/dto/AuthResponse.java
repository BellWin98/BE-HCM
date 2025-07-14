package com.behcm.domain.auth.dto;

import com.behcm.domain.member.dto.MemberResponse;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private MemberResponse member;

    public AuthResponse(String accessToken, String refreshToken, MemberResponse member) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.member = member;
    }
}
