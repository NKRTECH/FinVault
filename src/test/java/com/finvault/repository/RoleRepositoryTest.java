package com.finvault.repository;

import com.finvault.entity.Role;
import com.finvault.enums.RoleName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class RoleRepositoryTest {

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void shouldFindRoleByName() {
        // Given
        Role role = Role.builder().name(RoleName.ROLE_ADMIN).build();
        roleRepository.save(role);

        // When
        Optional<Role> found = roleRepository.findByName(RoleName.ROLE_ADMIN);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo(RoleName.ROLE_ADMIN);
    }
}
