package com.behcm.domain.admin.member.controller;

import com.behcm.domain.admin.member.dto.AdminMemberResponse;
import com.behcm.domain.admin.member.dto.UpdateMemberRoleRequest;
import com.behcm.domain.admin.member.service.AdminMemberService;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/members")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminMemberResponse>>> getMembers(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) MemberRole role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AdminMemberResponse> result = adminMemberService.getMembers(query, role, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PatchMapping("/{memberId}/role")
    public ResponseEntity<ApiResponse<AdminMemberResponse>> updateMemberRole(
            @PathVariable Long memberId,
            @Valid @RequestBody UpdateMemberRoleRequest request
    ) {
        AdminMemberResponse response = adminMemberService.updateMemberRole(memberId, request.getRole());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

