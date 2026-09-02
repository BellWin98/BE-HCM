package com.behcm.domain.admin.tossaccess.service;

import com.behcm.domain.admin.tossaccess.dto.AdminTossAccessResponse;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.domain.tossstock.entity.TossAccess;
import com.behcm.domain.tossstock.repository.TossAccessRepository;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 토스증권 접근 권한 부여/회수. ADMIN 은 이 테이블과 무관하게 항상 접근하므로
 * 여기서 다루는 것은 "ADMIN 이 지정한 유저" 목록이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminTossAccessService {

    private final TossAccessRepository tossAccessRepository;
    private final MemberRepository memberRepository;

    public List<AdminTossAccessResponse> getGrantedMembers() {
        return tossAccessRepository.findAllWithMember().stream()
                .map(AdminTossAccessResponse::from)
                .toList();
    }

    /**
     * 멱등하게 동작한다 — 이미 부여된 회원이면 기존 행을 그대로 돌려준다.
     * 토글 UI 에서 중복 요청이 와도 uk_toss_access_member 위반으로 500 이 나가지 않게 하기 위함이다.
     */
    @Transactional
    public AdminTossAccessResponse grant(Long memberId, Member admin) {
        Member target = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        TossAccess tossAccess = tossAccessRepository.findByMemberId(memberId)
                .orElseGet(() -> tossAccessRepository.save(TossAccess.builder()
                        .member(target)
                        .grantedBy(admin != null ? admin.getId() : null)
                        .build()));

        return AdminTossAccessResponse.from(tossAccess);
    }

    /** 멱등하게 동작한다 — 권한이 없던 회원이어도 성공으로 처리한다. */
    @Transactional
    public void revoke(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new CustomException(ErrorCode.MEMBER_NOT_FOUND);
        }
        tossAccessRepository.deleteByMemberId(memberId);
    }
}
