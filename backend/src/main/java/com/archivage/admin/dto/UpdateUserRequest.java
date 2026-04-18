package com.archivage.admin.dto;

import com.archivage.common.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Email String email,
        @Size(max = 200) String fullName,
        Role role,
        Long departmentId,
        Boolean active,
        @Size(min = 8, max = 200) String password
) {
}
