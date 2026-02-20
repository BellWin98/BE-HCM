package com.behcm.domain.member.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProfileImageUploadResponse {

    private String profileUrl;

    public static ProfileImageUploadResponse of(String profileUrl) {
        return ProfileImageUploadResponse.builder()
                .profileUrl(profileUrl)
                .build();
    }
}
