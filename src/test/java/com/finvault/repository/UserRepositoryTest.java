package com.finvault.repository;

import com.finvault.entity.User;
import com.finvault.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .username("test_user")
                .email("test@example.com")
                .password("hashed123")
                .fullName("Test User")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(testUser);
    }

    @Test
    void shouldFindUserByUsername() {
        Optional<User> found = userRepository.findByUsername("test_user");
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void shouldFindUserByEmail() {
        Optional<User> found = userRepository.findByEmail("test@example.com");
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("test_user");
    }

    @Test
    void shouldReturnTrueIfExistsByUsername() {
        boolean exists = userRepository.existsByUsername("test_user");
        assertThat(exists).isTrue();
    }

    @Test
    void shouldReturnTrueIfExistsByEmail() {
        boolean exists = userRepository.existsByEmail("test@example.com");
        assertThat(exists).isTrue();
    }

    @Test
    void shouldReturnFalseIfUsernameDoesNotExist() {
        boolean exists = userRepository.existsByUsername("unknown");
        assertThat(exists).isFalse();
    }

    @Test
    void shouldThrowExceptionWhenSavingDuplicateUsername() {
        User duplicateUser = User.builder()
                .username("test_user") // Duplicate username
                .email("new@example.com")
                .password("pass")
                .fullName("New User")
                .build();
        
        org.junit.jupiter.api.Assertions.assertThrows(
            org.springframework.dao.DataIntegrityViolationException.class, 
            () -> {
                userRepository.saveAndFlush(duplicateUser);
            }
        );
    }
}
