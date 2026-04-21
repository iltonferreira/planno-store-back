package com.planno.store.payment.account.dto;

import com.planno.store.payment.account.domain.PaymentProviderAccount;
import java.time.OffsetDateTime;

public record PaymentProviderAccountStatusResponse(
        String provider,
        String connectionStatus,
        OffsetDateTime connectedAt,
        String providerUserIdMasked,
        boolean hasValidAccessToken,
        OffsetDateTime tokenExpiresAt,
        boolean hasPublicKey,
        boolean readyForCheckout,
        boolean readyForSplit,
        boolean readyForTransparentCheckout
) {
    public static PaymentProviderAccountStatusResponse from(PaymentProviderAccount account) {
        String userId = account.getProviderUserId();
        String masked = userId == null || userId.length() < 4
                ? userId
                : "*".repeat(Math.max(0, userId.length() - 4)) + userId.substring(userId.length() - 4);
        boolean ready = account.hasValidAccessToken();
        boolean readyForTransparent = ready && account.getPublicKey() != null;
        return new PaymentProviderAccountStatusResponse(
                account.getProvider().name(),
                account.getConnectionStatus().name(),
                account.getConnectedAt(),
                masked,
                account.hasValidAccessToken(),
                account.getTokenExpiresAt(),
                account.getPublicKey() != null,
                ready,
                ready,
                readyForTransparent
        );
    }
}
