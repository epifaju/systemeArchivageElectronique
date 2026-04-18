package com.archivage.dashboard;

import com.archivage.auth.security.UserPrincipal;
import com.archivage.dashboard.dto.HomeDashboardDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public HomeDashboardDto home(@AuthenticationPrincipal UserPrincipal principal) {
        return dashboardService.home(principal);
    }
}
