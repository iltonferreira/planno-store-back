package com.planno.store.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email @jakarta.validation.constraints.Size(max = 255) String email,
        @NotBlank @jakarta.validation.constraints.Size(max = 128) String password,
        @jakarta.validation.constraints.Pattern(regexp = "|[0-9]{6}") String emailCode
) {
    public LoginRequest(String email, String password) { this(email, password, null); }
}

