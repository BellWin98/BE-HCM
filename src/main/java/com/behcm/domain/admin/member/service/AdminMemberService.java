package com.behcm.domain.admin.member.service;

import com.behcm.domain.admin.member.dto.AdminMemberResponse;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMemberService {

    private final MemberRepository memberRepository;

    public Page<AdminMemberResponse> getMembers(String query, MemberRole role, Pageable pageable) {
        String normalizedQuery = (query != null && !query.isBlank()) ? query : null;
        Page<Member> members = memberRepository.searchAdminMembers(normalizedQuery, role, pageable);
        return members.map(AdminMemberResponse::from);
    }

    @Transactional
    public AdminMemberResponse updateMemberRole(Long memberId, MemberRole newRole) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        member.changeRole(newRole);
        Member saved = memberRepository.save(member);

        return AdminMemberResponse.from(saved);
    }
}

