package com.smartelectro.smartelectro;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    @Test
    void loginPersistsAuthenticatedContextInHttpSession() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        GoogleIdentityService googleIdentityService = mock(GoogleIdentityService.class);
        User user = new User("buyer", "buyer@example.com", "encoded-password", Role.USER);
        when(authenticationManager.authenticate(any())).thenReturn(
                new UsernamePasswordAuthenticationToken("buyer@example.com", "password",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        when(userRepository.findByEmail("buyer@example.com")).thenReturn(Optional.of(user));
        AuthController controller = new AuthController(userRepository, passwordEncoder, authenticationManager, googleIdentityService);
        LoginRequest requestBody = new LoginRequest();
        requestBody.setEmail("buyer@example.com");
        requestBody.setPassword("password");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        var result = controller.authenticateUser(requestBody, request, response);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(request.getSession(false));
        Object savedContext = request.getSession(false)
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertTrue(savedContext instanceof SecurityContext);
        assertEquals("buyer@example.com", ((SecurityContext) savedContext).getAuthentication().getName());
    }

    @Test
    void googleSignInReturnsDisabledWhenClientIdIsNotConfigured() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        GoogleIdentityService googleIdentityService = mock(GoogleIdentityService.class);
        when(googleIdentityService.isConfigured()).thenReturn(false);
        AuthController controller = new AuthController(userRepository, mock(PasswordEncoder.class),
                mock(AuthenticationManager.class), googleIdentityService);

        var result = controller.authenticateWithGoogle(new GoogleCredentialRequest("credential", "nonce"),
                new MockHttpServletRequest(), new MockHttpServletResponse());

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, result.getStatusCode());
        verify(googleIdentityService, never()).verify("credential", "nonce");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void invalidGoogleCredentialDoesNotCreateSessionOrUser() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        GoogleIdentityService googleIdentityService = mock(GoogleIdentityService.class);
        when(googleIdentityService.isConfigured()).thenReturn(true);
        when(googleIdentityService.verify("invalid", "nonce")).thenReturn(null);
        AuthController controller = new AuthController(userRepository, mock(PasswordEncoder.class),
                mock(AuthenticationManager.class), googleIdentityService);
        MockHttpServletRequest request = new MockHttpServletRequest();

        var result = controller.authenticateWithGoogle(new GoogleCredentialRequest("invalid", "nonce"),
                request, new MockHttpServletResponse());

        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
        assertTrue(request.getSession(false) == null);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void verifiedGoogleIdentityCreatesUserAndPersistsSession() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        GoogleIdentityService googleIdentityService = mock(GoogleIdentityService.class);
        when(googleIdentityService.isConfigured()).thenReturn(true);
        when(googleIdentityService.verify("signed-token", "request-nonce"))
                .thenReturn(new GoogleIdentityService.GoogleIdentity("buyer@example.com", "Buyer"));
        when(userRepository.findByEmail("buyer@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-random-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AuthController controller = new AuthController(userRepository, passwordEncoder,
                mock(AuthenticationManager.class), googleIdentityService);
        MockHttpServletRequest request = new MockHttpServletRequest();

        var result = controller.authenticateWithGoogle(
                new GoogleCredentialRequest("signed-token", "request-nonce"),
                request, new MockHttpServletResponse());

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(request.getSession(false));
        assertTrue(request.getSession(false).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY) instanceof SecurityContext);
        verify(googleIdentityService).verify("signed-token", "request-nonce");
        verify(userRepository).save(any(User.class));
    }
}
