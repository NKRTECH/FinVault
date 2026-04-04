package com.finvault.service;

import com.finvault.dto.request.PasswordChangeRequest;
import com.finvault.dto.request.RoleAssignRequest;
import com.finvault.dto.request.UserUpdateRequest;
import com.finvault.dto.response.UserResponse;
import com.finvault.entity.Role;
import com.finvault.entity.User;
import com.finvault.enums.RoleName;
import com.finvault.enums.UserStatus;
import com.finvault.exception.DuplicateResourceException;
import com.finvault.exception.ResourceNotFoundException;
import com.finvault.repository.RoleRepository;
import com.finvault.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private Role viewerRole;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        viewerRole = Role.builder().id(1L).name(RoleName.ROLE_VIEWER).build();
        adminRole = Role.builder().id(2L).name(RoleName.ROLE_ADMIN).build();

        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@finvault.com")
                .password("encoded-password")
                .fullName("Test User")
                .status(UserStatus.ACTIVE)
                .roles(new HashSet<>(Set.of(viewerRole)))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ── getCurrentUser ───────────────────────────────────

    @Test
    void getCurrentUser_shouldReturnUserProfile() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        UserResponse response = userService.getCurrentUser("testuser");

        assertThat(response.getUsername()).isEqualTo("testuser");
        assertThat(response.getEmail()).isEqualTo("test@finvault.com");
        assertThat(response.getRoles()).contains("ROLE_VIEWER");
    }

    @Test
    void getCurrentUser_shouldThrowWhenNotFound() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getCurrentUser("unknown"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getAllUsers ───────────────────────────────────────

    @Test
    void getAllUsers_shouldReturnPaginatedResults() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> userPage = new PageImpl<>(List.of(testUser), pageable, 1);
        when(userRepository.findAll(pageable)).thenReturn(userPage);

        Page<UserResponse> result = userService.getAllUsers(pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUsername()).isEqualTo("testuser");
    }

    // ── getUserById ──────────────────────────────────────

    @Test
    void getUserById_shouldReturnUser() {
        when(userRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(testUser));

        UserResponse response = userService.getUserById(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getUsername()).isEqualTo("testuser");
    }

    @Test
    void getUserById_shouldThrowWhenNotFound() {
        when(userRepository.findByIdWithRoles(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── updateUser ───────────────────────────────────────

    @Test
    void updateUser_shouldUpdateEmailAndFullName() {
        when(userRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.existsByEmail("new@finvault.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        UserUpdateRequest request = new UserUpdateRequest("new@finvault.com", "New Name");
        UserResponse response = userService.updateUser(1L, request);

        assertThat(response).isNotNull();
        verify(userRepository).save(any(User.class));
    }

    @Test
    void updateUser_shouldThrowOnDuplicateEmail() {
        when(userRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.existsByEmail("taken@finvault.com")).thenReturn(true);

        UserUpdateRequest request = new UserUpdateRequest("taken@finvault.com", null);

        assertThatThrownBy(() -> userService.updateUser(1L, request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void updateUser_shouldSkipEmailUpdateWhenSameEmail() {
        when(userRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        UserUpdateRequest request = new UserUpdateRequest("test@finvault.com", "Updated Name");
        userService.updateUser(1L, request);

        verify(userRepository, never()).existsByEmail(any());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void updateUser_shouldThrowWhenUserNotFound() {
        when(userRepository.findByIdWithRoles(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(999L, new UserUpdateRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── updateUserStatus ─────────────────────────────────

    @Test
    void updateUserStatus_shouldChangeStatus() {
        when(userRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        UserResponse response = userService.updateUserStatus(1L, UserStatus.INACTIVE, "admin");

        assertThat(response).isNotNull();
        verify(userRepository).save(any(User.class));
    }

    @Test
    void updateUserStatus_shouldBlockSelfDeactivation() {
        when(userRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> userService.updateUserStatus(1L, UserStatus.INACTIVE, "testuser"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot change your own");
    }

    // ── assignRoles ──────────────────────────────────────

    @Test
    void assignRoles_shouldReplaceRoles() {
        when(userRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(testUser));
        when(roleRepository.findByName(RoleName.ROLE_ADMIN)).thenReturn(Optional.of(adminRole));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        RoleAssignRequest request = new RoleAssignRequest(Set.of("ROLE_ADMIN"));
        UserResponse response = userService.assignRoles(1L, request);

        assertThat(response).isNotNull();
        verify(userRepository).save(any(User.class));
    }

    @Test
    void assignRoles_shouldThrowOnInvalidRoleName() {
        when(userRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(testUser));

        RoleAssignRequest request = new RoleAssignRequest(Set.of("ROLE_NONEXISTENT"));

        assertThatThrownBy(() -> userService.assignRoles(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid role name");
    }

    // ── deleteUser ───────────────────────────────────────

    @Test
    void deleteUser_shouldSoftDelete() {
        when(userRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        userService.deleteUser(1L, "admin");

        verify(userRepository).save(argThat(user -> user.getStatus() == UserStatus.INACTIVE));
    }

    @Test
    void deleteUser_shouldBlockSelfDeletion() {
        when(userRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> userService.deleteUser(1L, "testuser"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot delete your own");
    }

    // ── changePassword ───────────────────────────────────

    @Test
    void changePassword_shouldUpdatePassword() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("old-password", "encoded-password")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-encoded");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        PasswordChangeRequest request = new PasswordChangeRequest("old-password", "new-password");
        userService.changePassword("testuser", request);

        verify(userRepository).save(argThat(user -> user.getPassword().equals("new-encoded")));
    }

    @Test
    void changePassword_shouldThrowOnWrongCurrentPassword() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        PasswordChangeRequest request = new PasswordChangeRequest("wrong-password", "new-password");

        assertThatThrownBy(() -> userService.changePassword("testuser", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Current password is incorrect");
    }
}
