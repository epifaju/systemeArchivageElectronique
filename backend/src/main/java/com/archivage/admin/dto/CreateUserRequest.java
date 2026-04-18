package com.archivage.admin.dto;

import com.archivage.common.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(min = 8, max = 200) String password,
        @Email String email,
        @Size(max = 200) String fullName,
        @NotNull Role role,
        Long departmentId,
        boolean active
) {
}
