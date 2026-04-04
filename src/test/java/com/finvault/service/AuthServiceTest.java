package com.finvault.service;

import com.finvault.dto.request.LoginRequest;
import com.finvault.dto.request.RegisterRequest;
import com.finvault.dto.response.AuthResponse;
import com.finvault.entity.Role;
import com.finvault.entity.User;
import com.finvault.enums.RoleName;
import com.finvault.enums.UserStatus;
import com.finvault.exception.DuplicateResourceException;
import com.finvault.repository.RoleRepository;
import com.finvault.repository.UserRepository;
import com.finvault.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private Role viewerRole;
    private User savedUser;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest("newuser", "new@mail.com", "pass123", "New User");
        loginRequest = new LoginRequest("admin", "admin123");

        viewerRole = Role.builder().id(3L).name(RoleName.ROLE_VIEWER).build();

        savedUser = User.builder()
                .id(1L)
                .username("newuser")
                .email("new@mail.com")
                .password("encoded_password")
                .fullName("New User")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(viewerRole))
                .build();
    }

    @Test
    void shouldRegisterNewUser() {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@mail.com")).thenReturn(false);
        when(roleRepository.findByName(RoleName.ROLE_VIEWER)).thenReturn(Optional.of(viewerRole));
        when(passwordEncoder.encode("pass123")).thenReturn("encoded_password");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(jwtTokenProvider.generateToken(authentication)).thenReturn("jwt-token");

        AuthResponse response = authService.register(registerRequest);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getUsername()).isEqualTo("newuser");
        assertThat(response.getEmail()).isEqualTo("new@mail.com");
        assertThat(response.getFullName()).isEqualTo("New User");
        assertThat(response.getRoles()).contains("ROLE_VIEWER");

        verify(userRepository).save(any(User.class));
        verify(passwordEncoder).encode("pass123");
    }

    @Test
    void shouldThrowWhenRegisteringDuplicateUsername() {
        when(userRepository.existsByUsername("newuser")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("username");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldThrowWhenRegisteringDuplicateEmail() {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@mail.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("email");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldLoginSuccessfully() {
        Role adminRole = Role.builder().id(1L).name(RoleName.ROLE_ADMIN).build();
        User adminUser = User.builder()
                .id(1L)
                .username("admin")
                .email("admin@finvault.com")
                .fullName("Admin")
                .roles(Set.of(adminRole))
                .build();

        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(jwtTokenProvider.generateToken(authentication)).thenReturn("jwt-token");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));

        AuthResponse response = authService.login(loginRequest);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getUsername()).isEqualTo("admin");
        assertThat(response.getRoles()).contains("ROLE_ADMIN");
    }

    @Test
    void shouldThrowWhenLoginWithWrongPassword() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class);
    }
}
