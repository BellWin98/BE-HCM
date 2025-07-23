package com.behcm.domain.member.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class MemberService {

    private final MemberRepository memberRepository;

    public void updateFcmToken(Member member, String fcmToken) {
        member.updateFcmToken(fcmToken);
        memberRepository.save(member);
    }
}
