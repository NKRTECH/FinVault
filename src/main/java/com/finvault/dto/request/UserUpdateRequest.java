package com.finvault.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequest {

    @Email(message = "Email must be valid")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    @Pattern(regexp = ".*\\S.*", message = "Email must not be blank")
    private String email;

    @Size(max = 100, message = "Full name must not exceed 100 characters")
    @Pattern(regexp = ".*\\S.*", message = "Full name must not be blank")
    private String fullName;
}
