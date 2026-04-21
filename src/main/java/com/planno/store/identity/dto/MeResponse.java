package com.planno.store.identity.dto;

import java.util.List;
import java.util.UUID;

public record MeResponse(
        UUID userId,
        String email,
        String firstName,
        String lastName,
        List<String> roles,
        List<UUID> storeIds
) {
}

