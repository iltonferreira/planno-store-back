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
        @NotBlank @Size(max = 150) String storeAdminName,
        @Size(max = 255) String publicDomain,
        @jakarta.validation.Valid
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Identity identity
) {
    public StoreProvisioningRequest(String idempotencyKey,String name,String slug,String contactEmail,String contactName,String companyName,String phone,String taxDocument,String planCode,String storeAdminEmail,String storeAdminName,String publicDomain) {
        this(idempotencyKey,name,slug,contactEmail,contactName,companyName,phone,taxDocument,planCode,storeAdminEmail,storeAdminName,publicDomain,null);
    }
    public record Identity(@Size(max=1000) String description,
                           @Pattern(regexp="https://[^\\s]+") @Size(max=2048) String logoUrl,
                           @Pattern(regexp="https://[^\\s]+") @Size(max=2048) String bannerUrl,
                           @Pattern(regexp="#[0-9a-fA-F]{6}") String primaryColor,
                           @Pattern(regexp="#[0-9a-fA-F]{6}") String secondaryColor) {}
}
