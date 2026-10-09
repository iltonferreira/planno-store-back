package com.planno.store.store.provisioning;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StoreProvisioningRequest(
        @NotBlank @Size(max = 120) String idempotencyKey,
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max = 63) String slug,
        @NotBlank @Email @Size(max = 255) String contactEmail,
        @NotBlank @Size(max = 150) String contactName,
        @NotBlank @Size(max = 150) String companyName,
        @Size(max = 32) String phone,
        @Size(max = 32) String taxDocument,
        @NotBlank @Pattern(regexp = "INITIAL|INTERMEDIATE|ADVANCED") String planCode,
        @NotBlank @Email @Size(max = 255) String storeAdminEmail,
        @NotBlank @Size(max = 150) String storeAdminName
) {
}
