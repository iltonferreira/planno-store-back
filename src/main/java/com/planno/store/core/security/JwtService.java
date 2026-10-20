package com.planno.store.core.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.Mac;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtProperties jwtProperties;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public String generateToken(AuthenticatedUser user) {
        Instant now = Instant.now();
        Instant expiration = now.plus(jwtProperties.expirationMinutes(), ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(user.getUsername())
                .claims(Map.of(
                        "userId", user.getUserId().toString(),
                        "emailTwoFactorVerified", true,
                        "credentialVersion", credentialVersion(user.getPassword()),
                        "storeIds", user.getStoreIds().stream().map(Object::toString).toList(),
                        "roles", user.getAuthorities().stream().map(Object::toString).toList()
                ))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(signingKey())
                .compact();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        Claims claims = extractAllClaims(token);
        String version = claims.get("credentialVersion", String.class);
        return userDetails.isEnabled() && userDetails.isAccountNonLocked() && userDetails.isAccountNonExpired()
                && userDetails.isCredentialsNonExpired() && userDetails.getUsername().equals(claims.getSubject())
                && Boolean.TRUE.equals(claims.get("emailTwoFactorVerified", Boolean.class))
                && claims.getExpiration() != null && claims.getExpiration().after(new Date())
                && version != null && MessageDigest.isEqual(version.getBytes(StandardCharsets.US_ASCII),
                    credentialVersion(userDetails.getPassword()).getBytes(StandardCharsets.US_ASCII));
    }

    private String credentialVersion(String passwordHash) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(signingKey());
            return HexFormat.of().formatHex(mac.doFinal(("credentials:" + passwordHash).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to validate session credentials", exception);
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey signingKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.secretBase64());
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public SecretKey deriveKeyForPurpose(String purpose) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(signingKey());
            return new javax.crypto.spec.SecretKeySpec(mac.doFinal(purpose.getBytes(StandardCharsets.UTF_8)), "HmacSHA256");
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to derive a scoped security key", exception);
        }
    }
}
