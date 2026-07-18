package com.behcm.global.security;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.global.common.TokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-for-jwt-token-provider-unit-test-must-be-long-enough";

    @Mock
    private UserDetailsService userDetailsService;

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET, 60_000L, 120_000L, userDetailsService);
    }

    private Member member(String email) {
        return Member.builder()
                .email(email)
                .password("encoded")
                .nickname("nick")
                .role(MemberRole.USER)
                .build();
    }

    @Test
    @DisplayName("generateTokensByEmail로 발급한 토큰은 유효하며 subject에 이메일이 담긴다")
    void generateTokensByEmail_returnsValidTokensContainingEmail() {
        TokenResponse tokens = jwtTokenProvider.generateTokensByEmail("user@test.com");

        assertThat(jwtTokenProvider.validateToken(tokens.getAccessToken())).isTrue();
        assertThat(jwtTokenProvider.validateToken(tokens.getRefreshToken())).isTrue();
        assertThat(jwtTokenProvider.getEmailFromJwt(tokens.getAccessToken())).isEqualTo("user@test.com");
        assertThat(jwtTokenProvider.getEmailFromJwt(tokens.getRefreshToken())).isEqualTo("user@test.com");
    }

    @Test
    @DisplayName("generateAccessToken은 Authentication의 principal(Member) 이메일을 subject로 담은 유효한 토큰을 만든다")
    void generateAccessToken_fromAuthentication_encodesMemberEmail() {
        Member member = member("owner@test.com");
        Authentication authentication = new UsernamePasswordAuthenticationToken(member, "", member.getAuthorities());

        String accessToken = jwtTokenProvider.generateAccessToken(authentication);

        assertThat(jwtTokenProvider.validateToken(accessToken)).isTrue();
        assertThat(jwtTokenProvider.getEmailFromJwt(accessToken)).isEqualTo("owner@test.com");
    }

    @Test
    @DisplayName("잘못된 형식의 토큰은 유효하지 않은 것으로 판단한다")
    void validateToken_malformedToken_returnsFalse() {
        assertThat(jwtTokenProvider.validateToken("not-a-jwt-token")).isFalse();
    }

    @Test
    @DisplayName("만료된 토큰은 validateToken에서 false를 반환하지만 getEmailFromJwt로는 subject를 읽을 수 있다")
    void expiredToken_isInvalidButSubjectIsStillReadable() throws InterruptedException {
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(SECRET, 1L, 1L, userDetailsService);
        TokenResponse tokens = shortLivedProvider.generateTokensByEmail("expiring@test.com");
        Thread.sleep(10);

        assertThat(shortLivedProvider.validateToken(tokens.getAccessToken())).isFalse();
        assertThat(shortLivedProvider.getEmailFromJwt(tokens.getAccessToken())).isEqualTo("expiring@test.com");
    }

    @Test
    @DisplayName("getAuthentication은 토큰의 이메일로 UserDetailsService를 조회해 인증 정보를 만든다")
    void getAuthentication_delegatesToUserDetailsService() {
        TokenResponse tokens = jwtTokenProvider.generateTokensByEmail("owner@test.com");
        UserDetails userDetails = User.withUsername("owner@test.com").password("pw").authorities("ROLE_USER").build();
        given(userDetailsService.loadUserByUsername("owner@test.com")).willReturn(userDetails);

        Authentication authentication = jwtTokenProvider.getAuthentication(tokens.getAccessToken());

        assertThat(authentication.getPrincipal()).isEqualTo(userDetails);
        assertThat(authentication.getName()).isEqualTo("owner@test.com");
    }
}
