package com.behcm.domain.admin.member.dto;

import com.behcm.domain.member.entity.MemberRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateMemberRoleRequest {

    @NotNull(message = "역할은 필수입니다.")
    private MemberRole role;
}

