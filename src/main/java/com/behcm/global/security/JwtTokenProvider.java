package com.behcm.global.security;

import com.behcm.domain.member.entity.Member;
import com.behcm.global.common.TokenResponse;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
@Slf4j
public class JwtTokenProvider {

    private final Key key;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;
    private final UserDetailsService userDetailsService;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secretKey,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration,
            UserDetailsService userDetailsService
    ) {
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes());
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.userDetailsService = userDetailsService;
    }

    public String generateAccessToken(Authentication authentication) {
        return generateToken(authentication, accessTokenExpiration);
    }

    public String generateRefreshToken(Authentication authentication) {
        return generateToken(authentication, refreshTokenExpiration);
    }

    public TokenResponse generateTokensByEmail(String email) {
        Date accessTokenExpiryDate = new Date(System.currentTimeMillis() + accessTokenExpiration);
        Date refreshTokenExpiryDate = new Date(System.currentTimeMillis() + refreshTokenExpiration);
        String accessToken = Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(accessTokenExpiryDate)
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();
        String refreshToken = Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(refreshTokenExpiryDate)
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();

        return new TokenResponse(accessToken, refreshToken);
    }

    public String getEmailFromJwt(String token) {
        Claims claims = parseClaims(token);

        return claims.getSubject();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);

            return true;
        } catch (SecurityException ex) {
            log.error("Invalid JWT signature: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            log.error("Invalid JWT token: {}", ex.getMessage());
        } catch (ExpiredJwtException ex) {
            log.error("Expired JWT token: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            log.error("Unsupported JWT token: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty: {}", ex.getMessage());
        }
        return false;
    }

    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);
        String memberEmail = claims.getSubject();
        UserDetails userDetails = userDetailsService.loadUserByUsername(memberEmail);

        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
    }

    private String generateToken(Authentication authentication, long tokenExpiration) {
        Member member = (Member) authentication.getPrincipal();
        Claims claims = Jwts.claims().setSubject(member.getUsername());
        claims.put("roles", member.getAuthorities());
        Date expiryDate = new Date(System.currentTimeMillis() + tokenExpiration);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();
    }

    private Claims parseClaims(String accessToken) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(accessToken)
                    .getBody();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }
}
