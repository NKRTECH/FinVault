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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        return mapToUserResponse(user);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(this::mapToUserResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = findUserByIdOrThrow(id);
        return mapToUserResponse(user);
    }

    @Transactional
    public UserResponse updateUser(Long id, UserUpdateRequest request) {
        User user = findUserByIdOrThrow(id);

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new DuplicateResourceException("User", "email", request.getEmail());
            }
            user.setEmail(request.getEmail());
        }

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }

        User updated = userRepository.save(user);
        log.info("User updated: id={}", id);
        return mapToUserResponse(updated);
    }

    @Transactional
    public UserResponse updateUserStatus(Long id, UserStatus status, String currentUsername) {
        User user = findUserByIdOrThrow(id);

        if (user.getUsername().equals(currentUsername)) {
            throw new AccessDeniedException("You cannot change your own account status");
        }

        user.setStatus(status);
        User updated = userRepository.save(user);
        log.info("User status updated: id={}, status={}", id, status);
        return mapToUserResponse(updated);
    }

    @Transactional
    public UserResponse assignRoles(Long id, RoleAssignRequest request) {
        User user = findUserByIdOrThrow(id);

        Set<Role> newRoles = new HashSet<>();
        for (String roleName : request.getRoles()) {
            RoleName roleEnum;
            try {
                roleEnum = RoleName.valueOf(roleName);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid role name: " + roleName);
            }
            Role role = roleRepository.findByName(roleEnum)
                    .orElseThrow(() -> new ResourceNotFoundException("Role", "name", roleName));
            newRoles.add(role);
        }

        user.setRoles(newRoles);
        User updated = userRepository.save(user);
        log.info("Roles assigned to user id={}: {}", id, request.getRoles());
        return mapToUserResponse(updated);
    }

    @Transactional
    public void deleteUser(Long id, String currentUsername) {
        User user = findUserByIdOrThrow(id);

        if (user.getUsername().equals(currentUsername)) {
            throw new AccessDeniedException("You cannot delete your own account");
        }

        user.setStatus(UserStatus.INACTIVE);
        userRepository.save(user);
        log.info("User soft-deleted: id={}", id);
    }

    @Transactional
    public void changePassword(String username, PasswordChangeRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed for user: {}", username);
    }

    private User findUserByIdOrThrow(Long id) {
        return userRepository.findByIdWithRoles(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id.toString()));
    }

    private UserResponse mapToUserResponse(User user) {
        Set<String> roles = user.getRoles().stream()
                .map(role -> role.getName().name())
                .collect(Collectors.toSet());

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .status(user.getStatus().name())
                .roles(roles)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
