package com.behcm.domain.rest.controller;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.rest.dto.RestRequest;
import com.behcm.domain.rest.dto.RestResponse;
import com.behcm.domain.rest.service.RestService;
import com.behcm.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@org.springframework.web.bind.annotation.RestController
@RequiredArgsConstructor
@RequestMapping("/api/rest")
public class RestController {

    private final RestService restService;

    @PostMapping
    public ResponseEntity<ApiResponse<RestResponse>> registerRestDay(
            @Valid @RequestBody RestRequest request,
            @AuthenticationPrincipal Member member
    ) {
        RestResponse response = restService.registerRestDay(member, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
