package com.smartelectro.smartelectro;

import jakarta.validation.Valid;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final HttpSessionSecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final GoogleIdentityService googleIdentityService;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          AuthenticationManager authenticationManager, GoogleIdentityService googleIdentityService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.googleIdentityService = googleIdentityService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            return ResponseEntity.badRequest().body("Email is already in use!");
        }

        User user = new User(
                registerRequest.getEmail().split("@")[0],
                registerRequest.getEmail(),
                passwordEncoder.encode(registerRequest.getPassword()),
                Role.USER
        );

        userRepository.save(user);

        return ResponseEntity.ok("User registered successfully!");
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest,
                                              HttpServletRequest request, HttpServletResponse response) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword())
            );
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);

            User user = userRepository.findByEmail(loginRequest.getEmail())
                    .orElseThrow(() -> new RuntimeException("User not found!"));

            AuthResponse authResponse = new AuthResponse(
                    user.getUsername(),
                    user.getEmail(),
                    user.getRole().name()
            );

            return ResponseEntity.ok(authResponse);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Invalid email or password!");
        }
    }

    @GetMapping("/google/config")
    public GoogleSignInConfig googleSignInConfig() {
        return new GoogleSignInConfig(googleIdentityService.isConfigured(),
                googleIdentityService.isConfigured() ? googleIdentityService.getClientId() : null);
    }

    @PostMapping("/google")
    public ResponseEntity<?> authenticateWithGoogle(@Valid @RequestBody GoogleCredentialRequest requestBody,
                                                    HttpServletRequest request, HttpServletResponse response) {
        if (!googleIdentityService.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Google sign-in is not configured. Please use email and password.");
        }

        final GoogleIdentityService.GoogleIdentity identity;
        try {
            identity = googleIdentityService.verify(requestBody.credential(), requestBody.nonce());
        } catch (IOException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Google sign-in verification is temporarily unavailable. Please try again.");
        } catch (GeneralSecurityException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Google credential could not be verified.");
        }
        if (identity == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Google credential is invalid or expired.");
        }

        User user = userRepository.findByEmail(identity.email()).orElseGet(() -> {
            String emailName = identity.email().substring(0, identity.email().indexOf('@'));
            String username = (emailName.isBlank() ? "google-user" : emailName) + "-" +
                    UUID.randomUUID().toString().substring(0, 8);
            String encodedRandomPassword = passwordEncoder.encode(UUID.randomUUID().toString());
            return userRepository.save(new User(username, identity.email(), encodedRandomPassword, Role.USER));
        });

        Authentication authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return ResponseEntity.ok(new AuthResponse(user.getUsername(), user.getEmail(), user.getRole().name()));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() ||
            authentication.getName().equals("anonymousUser")) {
            return ResponseEntity.noContent().build();
        }

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found!"));

        return ResponseEntity.ok(new AuthResponse(
                user.getUsername(),
                user.getEmail(),
                user.getRole().name()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logoutUser() {
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok("Logged out successfully!");
    }

    public record GoogleSignInConfig(boolean enabled, String clientId) {
    }
}
