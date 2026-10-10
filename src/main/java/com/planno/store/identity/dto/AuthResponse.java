package com.planno.store.identity.dto;

import java.util.List;
import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInMinutes,
        UUID userId,
        String email,
        List<String> roles,
        List<UUID> storeIds,
        boolean mustChangePassword
) {
}
