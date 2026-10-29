package com.smartelectro.smartelectro;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Locale;

@Service
public class GoogleIdentityService {

    private final String clientId;
    private final GoogleIdTokenVerifier verifier;

    public GoogleIdentityService(@Value("${smartelectro.google.client-id:}") String clientId) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.verifier = this.clientId.isEmpty()
                ? null
                : new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                        .setAudience(Collections.singletonList(this.clientId))
                        .build();
    }

    public boolean isConfigured() {
        return verifier != null;
    }

    public String getClientId() {
        return clientId;
    }

    public GoogleIdentity verify(String credential, String expectedNonce) throws GeneralSecurityException, IOException {
        if (verifier == null) {
            throw new IllegalStateException("Google sign-in is not configured.");
        }
        if (credential == null || credential.isBlank() || expectedNonce == null || expectedNonce.isBlank()) {
            return null;
        }

        GoogleIdToken idToken = verifier.verify(credential);
        if (idToken == null) {
            return null;
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        Object verifiedClaim = payload.get("email_verified");
        Object nonceClaim = payload.get("nonce");
        if (!Boolean.TRUE.equals(verifiedClaim) || payload.getEmail() == null || payload.getEmail().isBlank()
                || !(nonceClaim instanceof String tokenNonce)
                || !MessageDigest.isEqual(expectedNonce.getBytes(StandardCharsets.UTF_8),
                        tokenNonce.getBytes(StandardCharsets.UTF_8))) {
            return null;
        }

        String email = payload.getEmail().trim().toLowerCase(Locale.ROOT);
        String name = payload.get("name") instanceof String value ? value.trim() : "";
        return new GoogleIdentity(email, name);
    }

    public record GoogleIdentity(String email, String name) {
    }
}
