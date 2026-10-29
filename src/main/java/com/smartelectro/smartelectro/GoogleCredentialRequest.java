package com.smartelectro.smartelectro;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleCredentialRequest(
        @NotBlank @Size(max = 10000) String credential,
        @NotBlank @Size(max = 128) String nonce) {
}
