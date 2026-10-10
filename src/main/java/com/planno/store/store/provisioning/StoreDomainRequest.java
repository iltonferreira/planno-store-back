package com.planno.store.store.provisioning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StoreDomainRequest(
        @NotBlank @Size(max = 253)
        @Pattern(regexp = "(?i)(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}")
        String domain) {
}
