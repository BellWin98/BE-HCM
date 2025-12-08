package com.behcm.domain.notification.entity;

import com.behcm.domain.member.entity.Member;
import com.behcm.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = {
        @Index(name = "idx_member_id", columnList = "member_id")
})
public class FcmToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = 500, unique = true)
    private String token;

    @Column(nullable = false)
    private Boolean isActive = true;

    @Builder
    public FcmToken(Member member, String token) {
        this.member = member;
        this.token = token;
    }

    public void updateToken(String token) {
        this.token = token;
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }
}