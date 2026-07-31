package com.booking.azure.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {
    
    @Value("${jwt.secret:defaultSecretKeyForDevelopmentOnlyMakeItLongerThan32Bytes}")
    private String secret;

    @Value("${jwt.expiration.access:900000}") // 15 mins default
    private Long accessExpirationMs;

    @Value("${jwt.expiration.refresh:604800000}") // 7 days default
    private Long refreshExpirationMs;

    private Key getSigningKey() {
        if (secret == null || secret.length() < 32) {
            return Keys.secretKeyFor(SignatureAlgorithm.HS256);
        }
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateAccessToken(String username) {
        return createToken(new HashMap<>(), username, accessExpirationMs);
    }

    public String generateRefreshToken(String username) {
        return createToken(new HashMap<>(), username, refreshExpirationMs);
    }

    private String createToken(Map<String, Object> claims, String subject, Long expiration) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public Boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token).getBody();
        return claimsResolver.apply(claims);
    }
}


