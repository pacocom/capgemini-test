package com.capgemini.test.code.users;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
    @NotBlank @Size(max = 6) String name,
    @NotBlank @Size(max = 150) @Pattern(regexp = "(?s)^(?=.*@)(?=.*\\.).*$") String email,
    @NotBlank @Size(max = 15) String phone,
    @NotBlank @Pattern(regexp = "admin|superadmin") String rol,
    @NotBlank @Size(max = 15) String dni) {
}
