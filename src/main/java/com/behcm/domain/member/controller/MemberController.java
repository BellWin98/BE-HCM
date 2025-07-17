package com.behcm.domain.member.controller;

import com.behcm.domain.member.dto.MemberResponse;
import com.behcm.domain.member.entity.Member;
import com.behcm.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members")
public class MemberController {

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberResponse>> getMyInfo(
            @AuthenticationPrincipal Member member
            ) {
        MemberResponse response = MemberResponse.from(member);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

}
