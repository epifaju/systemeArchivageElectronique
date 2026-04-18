package com.archivage.auth.dto;

import com.archivage.common.domain.Role;

import java.util.UUID;

public record UserSummaryDto(
        Long id,
        UUID uuid,
        String username,
        String email,
        String fullName,
        Role role
) {
}
