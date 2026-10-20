package com.planno.store.identity.service;

import com.planno.store.identity.domain.EmailTwoFactorChallenge;
import com.planno.store.identity.domain.UserStatus;
import com.planno.store.identity.repository.EmailTwoFactorChallengeRepository;
import com.planno.store.identity.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailTwoFactorService {
    private static final long CODE_TTL_SECONDS = 600;
    private static final long RESEND_COOLDOWN_SECONDS = 60;
    private static final int MAX_ATTEMPTS = 5;
    private final UserRepository users;
    private final EmailTwoFactorChallengeRepository challenges;
    private final IdentityTwoFactorMailer mailer;
    private final SecretKey hmacKey;
    private final SecureRandom random = new SecureRandom();

    public EmailTwoFactorService(UserRepository users, EmailTwoFactorChallengeRepository challenges,
            IdentityTwoFactorMailer mailer, com.planno.store.core.security.JwtService jwtService) {
        this.users = users;
        this.challenges = challenges;
        this.mailer = mailer;
        this.hmacKey = jwtService.deriveKeyForPurpose("planno-store:email-otp:v1");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendCode(UUID userId) {
        var user = users.findByIdForUpdate(userId).filter(account -> account.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException("Invalid email or password"));
        Instant now = Instant.now();
        var challenge = challenges.findByUserIdForUpdate(userId).orElse(null);
        if (challenge != null && challenge.isActiveAt(now)
                && challenge.getIssuedAt().plusSeconds(RESEND_COOLDOWN_SECONDS).isAfter(now)) return;

        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        if (challenge == null) {
            challenge = new EmailTwoFactorChallenge(user, hmac(userId, user.getEmail(), code), now,
                    now.plusSeconds(CODE_TTL_SECONDS));
        } else {
            challenge.replace(hmac(userId, user.getEmail(), code), now, now.plusSeconds(CODE_TTL_SECONDS));
        }
        challenges.saveAndFlush(challenge);
        mailer.send(user, code);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean consumeCode(UUID userId, String submittedCode) {
        if (submittedCode == null || !submittedCode.matches("[0-9]{6}")) return false;
        var challenge = challenges.findByUserIdForUpdate(userId).orElse(null);
        Instant now = Instant.now();
        if (challenge == null || !challenge.isActiveAt(now)) return false;
        byte[] expected = hmac(userId, challenge.getUser().getEmail(), submittedCode).getBytes(StandardCharsets.US_ASCII);
        byte[] stored = challenge.getCodeHmac().getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, stored)) {
            challenge.recordFailure();
            return false;
        }
        challenge.consume(now);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void invalidate(UUID userId) {
        challenges.findByUserIdForUpdate(userId).ifPresent(challenge -> challenge.consume(Instant.now()));
    }

    private String hmac(UUID userId, String email, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            return HexFormat.of().formatHex(mac.doFinal((userId + ":" + email.trim().toLowerCase(Locale.ROOT) + ":" + code)
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException impossible) {
            throw new IllegalStateException("Unable to protect login code", impossible);
        }
    }
}
