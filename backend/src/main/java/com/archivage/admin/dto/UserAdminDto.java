package com.archivage.admin.dto;

import com.archivage.common.domain.Role;

import java.util.UUID;

public record UserAdminDto(
        Long id,
        UUID uuid,
        String username,
        String email,
        String fullName,
        Role role,
        Long departmentId,
        boolean active
) {
}
