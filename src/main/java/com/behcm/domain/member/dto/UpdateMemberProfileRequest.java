package com.behcm.domain.member.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateMemberProfileRequest {

    @NotEmpty(message = "닉네임은 필수입니다.")
    @Size(min = 2, max = 10, message = "닉네임은 2-10자 사이여야 합니다.")
    private String nickname;

    private String bio;
    private String profileUrl;
}
