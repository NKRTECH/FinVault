package com.finvault.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleAssignRequest {

    @NotEmpty(message = "At least one role must be specified")
    private Set<String> roles;
}
