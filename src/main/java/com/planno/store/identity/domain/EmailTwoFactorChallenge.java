package com.planno.store.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "user_email_2fa_challenges")
public class EmailTwoFactorChallenge implements Persistable<UUID> {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(name = "code_hmac", nullable = false, length = 64)
    private String codeHmac;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Transient
    private boolean newEntity = true;

    protected EmailTwoFactorChallenge() {}

    public EmailTwoFactorChallenge(User user, String codeHmac, Instant issuedAt, Instant expiresAt) {
        this.userId = user.getId();
        this.user = user;
        this.codeHmac = codeHmac;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    @Override public UUID getId() { return userId; }
    @Override public boolean isNew() { return newEntity; }
    @PostLoad @PostPersist private void markExisting() { newEntity = false; }

    public User getUser() { return user; }
    public String getCodeHmac() { return codeHmac; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public int getFailedAttempts() { return failedAttempts; }
    public Instant getConsumedAt() { return consumedAt; }

    public boolean isActiveAt(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now) && failedAttempts < 5;
    }

    public void replace(String newCodeHmac, Instant now, Instant newExpiry) {
        codeHmac = newCodeHmac;
        issuedAt = now;
        expiresAt = newExpiry;
        failedAttempts = 0;
        consumedAt = null;
    }

    public void recordFailure() { failedAttempts = Math.min(5, failedAttempts + 1); }
    public void consume(Instant now) { consumedAt = now; }
}
