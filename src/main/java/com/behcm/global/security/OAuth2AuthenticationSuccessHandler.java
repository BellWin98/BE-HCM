package com.behcm.global.security;

import com.behcm.domain.auth.dto.AuthResponse;
import com.behcm.domain.auth.service.RefreshTokenService;
import com.behcm.domain.member.dto.MemberResponse;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.global.common.TokenResponse;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final MemberRepository memberRepository;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String registrationId = getRegistrationId(authentication);

        OAuth2UserInfo userInfo = extractUserInfo(oauth2User, registrationId);
        Member member = findOrCreateMember(userInfo, registrationId);

        TokenResponse tokenResponse = tokenProvider.generateTokensByEmail(member.getEmail());
        refreshTokenService.storeRefreshToken(member.getEmail(), tokenResponse.getRefreshToken());

        AuthResponse authResponse = new AuthResponse(
                tokenResponse.getAccessToken(),
                tokenResponse.getRefreshToken(),
                MemberResponse.from(member)
        );

        String redirectUrl = buildRedirectUrl(authResponse);
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }

    private String getRegistrationId(Authentication authentication) {
        return ((OAuth2AuthenticationToken) authentication).getAuthorizedClientRegistrationId();
    }

    private OAuth2UserInfo extractUserInfo(OAuth2User oauth2User, String registrationId) {
        Map<String, Object> attributes = oauth2User.getAttributes();

        return switch (registrationId.toLowerCase()) {
            case "google" -> extractGoogleUserInfo(attributes);
            case "kakao" -> extractKakaoUserInfo(attributes);
            default -> throw new CustomException(ErrorCode.INVALID_OAUTH_PROVIDER);
        };
    }

    private OAuth2UserInfo extractGoogleUserInfo(Map<String, Object> attributes) {
        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");
        String picture = (String) attributes.get("picture");
        String providerId = String.valueOf(attributes.get("sub"));

        return new OAuth2UserInfo(email, name, picture, providerId);
    }

    @SuppressWarnings("unchecked")
    private OAuth2UserInfo extractKakaoUserInfo(Map<String, Object> attributes) {
        String providerId = String.valueOf(attributes.get("id"));

        Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
        String email = kakaoAccount != null && kakaoAccount.get("email") != null
                ? (String) kakaoAccount.get("email")
                : "kakao_" + providerId + "@oauth.local";

        String name = null;
        String picture = null;
        if (kakaoAccount != null) {
            Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
            if (profile != null) {
                name = (String) profile.get("nickname");
                picture = (String) profile.get("profile_image_url");
            }
        }

        return new OAuth2UserInfo(email, name, picture, providerId);
    }

    private Member findOrCreateMember(OAuth2UserInfo userInfo, String provider) {
        return memberRepository.findByOauthProviderAndOauthProviderId(provider, userInfo.providerId())
                .or(() -> memberRepository.findByEmail(userInfo.email()))
                .map(member -> updateOAuthMember(member, userInfo, provider))
                .orElseGet(() -> createOAuthMember(userInfo, provider));
    }

    private Member updateOAuthMember(Member member, OAuth2UserInfo userInfo, String provider) {
        if (member.getOauthProvider() == null) {
            member.updateOAuthInfo(provider, userInfo.providerId());
        }
        return member;
    }

    private Member createOAuthMember(OAuth2UserInfo userInfo, String provider) {
        String nickname = generateUniqueNickname(userInfo.name());
        String password = passwordEncoder.encode(UUID.randomUUID().toString());

        Member member = Member.builder()
                .email(userInfo.email())
                .password(password)
                .nickname(nickname)
                .profileUrl(userInfo.picture() != null ? userInfo.picture() : "")
                .role(MemberRole.USER)
                .oauthProvider(provider)
                .oauthProviderId(userInfo.providerId())
                .build();

        return memberRepository.save(member);
    }

    private static final int MAX_NICKNAME_LENGTH = 10;

    private String generateUniqueNickname(String baseName) {
        String base = (baseName != null && !baseName.isBlank())
                ? baseName.replaceAll("[^a-zA-Z0-9가-힣]", "")
                : "user";
        base = base.length() > MAX_NICKNAME_LENGTH - 4 ? base.substring(0, MAX_NICKNAME_LENGTH - 4) : base;
        String nickname = base;
        int suffix = 1;
        while (memberRepository.existsByNickname(nickname)) {
            nickname = (base + suffix++);
            nickname = nickname.length() > MAX_NICKNAME_LENGTH ? nickname.substring(0, MAX_NICKNAME_LENGTH) : nickname;
        }
        return nickname;
    }

    private String buildRedirectUrl(AuthResponse authResponse) {
        return UriComponentsBuilder.fromUriString(frontendUrl + "/oauth/callback")
                .queryParam("access_token", authResponse.getAccessToken())
                .queryParam("refresh_token", authResponse.getRefreshToken())
                .build()
                .toUriString();
    }

    private record OAuth2UserInfo(String email, String name, String picture, String providerId) {}
}
