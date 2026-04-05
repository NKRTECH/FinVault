package com.finvault.controller;

import com.finvault.dto.request.PasswordChangeRequest;
import com.finvault.dto.request.RoleAssignRequest;
import com.finvault.dto.request.UserUpdateRequest;
import com.finvault.dto.response.ApiResponse;
import com.finvault.dto.response.UserResponse;
import com.finvault.enums.UserStatus;
import com.finvault.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "User profile, admin user CRUD, roles, and status management")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    // ── Self-service endpoints ────────────────────────────

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get current user", description = "Returns the authenticated user's profile")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        UserResponse response = userService.getCurrentUser(username);
        return ResponseEntity.ok(ApiResponse.success("Profile retrieved successfully", response));
    }

    @PutMapping("/me/password")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Change password", description = "Change the authenticated user's password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody PasswordChangeRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        userService.changePassword(username, request);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", null));
    }

    // ── Admin endpoints ──────────────────────────────────

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all users", description = "Paginated list of all users (ADMIN only)")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getAllUsers(Pageable pageable) {
        Page<UserResponse> users = userService.getAllUsers(pageable);
        return ResponseEntity.ok(ApiResponse.success("Users retrieved successfully", users));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get user by ID", description = "Retrieve a specific user (ADMIN only)")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Long id) {
        UserResponse response = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success("User retrieved successfully", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update user", description = "Update user details (ADMIN only)")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserUpdateRequest request) {
        UserResponse response = userService.updateUser(id, request);
        return ResponseEntity.ok(ApiResponse.success("User updated successfully", response));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update user status", description = "Set user to ACTIVE, INACTIVE, or SUSPENDED (ADMIN only)")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserStatus(
            @PathVariable Long id,
            @RequestParam UserStatus status) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        UserResponse response = userService.updateUserStatus(id, status, username);
        return ResponseEntity.ok(ApiResponse.success("User status updated successfully", response));
    }

    @PatchMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Assign roles", description = "Assign roles to a user (ADMIN only)")
    public ResponseEntity<ApiResponse<UserResponse>> assignRoles(
            @PathVariable Long id,
            @Valid @RequestBody RoleAssignRequest request) {
        UserResponse response = userService.assignRoles(id, request);
        return ResponseEntity.ok(ApiResponse.success("Roles assigned successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete user", description = "Delete a user account (ADMIN only, cannot self-delete)")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        userService.deleteUser(id, username);
        return ResponseEntity.ok(ApiResponse.success("User deleted successfully", null));
    }
}
