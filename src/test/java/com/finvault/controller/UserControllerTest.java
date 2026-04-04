package com.finvault.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finvault.dto.request.PasswordChangeRequest;
import com.finvault.dto.request.RoleAssignRequest;
import com.finvault.dto.request.UserUpdateRequest;
import com.finvault.dto.response.UserResponse;
import com.finvault.enums.UserStatus;
import com.finvault.exception.GlobalExceptionHandler;
import com.finvault.exception.ResourceNotFoundException;
import com.finvault.security.CustomUserDetailsService;
import com.finvault.security.JwtAuthenticationFilter;
import com.finvault.security.JwtTokenProvider;
import com.finvault.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private UserResponse testUserResponse;

    @BeforeEach
    void setUp() {
        testUserResponse = UserResponse.builder()
                .id(1L)
                .username("testuser")
                .email("test@finvault.com")
                .fullName("Test User")
                .status("ACTIVE")
                .roles(Set.of("ROLE_VIEWER"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Clear any existing authentication so each test sets its own security context explicitly
        SecurityContextHolder.clearContext();
    }

    private void setAuthentication(String username, String role) {
        var auth = new UsernamePasswordAuthenticationToken(
                username, null, List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ── GET /me ──────────────────────────────────────────

    @Test
    void getMe_shouldReturnCurrentUserProfile() throws Exception {
        setAuthentication("testuser", "ROLE_VIEWER");
        when(userService.getCurrentUser("testuser")).thenReturn(testUserResponse);

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.data.email").value("test@finvault.com"));
    }

    // ── PUT /me/password ─────────────────────────────────

    @Test
    void changePassword_shouldReturn200() throws Exception {
        setAuthentication("testuser", "ROLE_VIEWER");
        doNothing().when(userService).changePassword(eq("testuser"), any(PasswordChangeRequest.class));

        PasswordChangeRequest request = new PasswordChangeRequest("oldpass", "newpassword");

        mockMvc.perform(put("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Password changed successfully"));
    }

    @Test
    void changePassword_shouldReturn400OnValidationError() throws Exception {
        setAuthentication("testuser", "ROLE_VIEWER");
        PasswordChangeRequest request = new PasswordChangeRequest("", "");

        mockMvc.perform(put("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── GET / (admin) ────────────────────────────────────

    @Test
    void getAllUsers_shouldReturnPaginatedList() throws Exception {
        Page<UserResponse> page = new PageImpl<>(List.of(testUserResponse));
        when(userService.getAllUsers(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].username").value("testuser"));
    }

    // ── GET /{id} (admin) ────────────────────────────────

    @Test
    void getUserById_shouldReturnUser() throws Exception {
        when(userService.getUserById(1L)).thenReturn(testUserResponse);

        mockMvc.perform(get("/api/v1/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.username").value("testuser"));
    }

    @Test
    void getUserById_shouldReturn404WhenNotFound() throws Exception {
        when(userService.getUserById(999L)).thenThrow(new ResourceNotFoundException("User", "id", "999"));

        mockMvc.perform(get("/api/v1/users/999"))
                .andExpect(status().isNotFound());
    }

    // ── PUT /{id} (admin) ────────────────────────────────

    @Test
    void updateUser_shouldReturnUpdatedUser() throws Exception {
        when(userService.updateUser(eq(1L), any(UserUpdateRequest.class))).thenReturn(testUserResponse);

        UserUpdateRequest request = new UserUpdateRequest("new@finvault.com", "New Name");

        mockMvc.perform(put("/api/v1/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ── PATCH /{id}/status (admin) ───────────────────────

    @Test
    void updateUserStatus_shouldReturn200() throws Exception {
        setAuthentication("admin", "ROLE_ADMIN");
        when(userService.updateUserStatus(eq(1L), eq(UserStatus.INACTIVE), eq("admin")))
                .thenReturn(testUserResponse);

        mockMvc.perform(patch("/api/v1/users/1/status")
                        .param("status", "INACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ── PATCH /{id}/roles (admin) ────────────────────────

    @Test
    void assignRoles_shouldReturn200() throws Exception {
        when(userService.assignRoles(eq(1L), any(RoleAssignRequest.class))).thenReturn(testUserResponse);

        RoleAssignRequest request = new RoleAssignRequest(Set.of("ROLE_ADMIN"));

        mockMvc.perform(patch("/api/v1/users/1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ── DELETE /{id} (admin) ─────────────────────────────

    @Test
    void deleteUser_shouldReturn200() throws Exception {
        setAuthentication("admin", "ROLE_ADMIN");
        doNothing().when(userService).deleteUser(eq(1L), eq("admin"));

        mockMvc.perform(delete("/api/v1/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User deleted successfully"));
    }

    // ── Validation tests ─────────────────────────────────

    @Test
    void updateUser_shouldReturn400ForInvalidEmail() throws Exception {
        UserUpdateRequest request = new UserUpdateRequest("not-an-email", null);

        mockMvc.perform(put("/api/v1/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assignRoles_shouldReturn400WhenEmpty() throws Exception {
        String body = "{\"roles\":[]}";

        mockMvc.perform(patch("/api/v1/users/1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
