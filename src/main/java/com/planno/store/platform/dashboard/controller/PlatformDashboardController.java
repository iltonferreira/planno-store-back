package com.planno.store.platform.dashboard.controller;

import com.planno.store.core.time.DashboardPeriodPreset;
import com.planno.store.platform.dashboard.dto.PlatformCommissionByStoreResponse;
import com.planno.store.platform.dashboard.dto.PlatformDashboardSummaryResponse;
import com.planno.store.platform.dashboard.dto.PlatformSalesByDayResponse;
import com.planno.store.platform.dashboard.dto.PlatformTopStoreResponse;
import com.planno.store.platform.dashboard.service.PlatformDashboardService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/dashboard")
public class PlatformDashboardController {

    private final PlatformDashboardService platformDashboardService;

    public PlatformDashboardController(PlatformDashboardService platformDashboardService) {
        this.platformDashboardService = platformDashboardService;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<PlatformDashboardSummaryResponse> summary(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) DashboardPeriodPreset preset
    ) {
        return ResponseEntity.ok(platformDashboardService.summary(from, to, preset));
    }

    @GetMapping("/commission-by-store")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<PlatformCommissionByStoreResponse>> commissionByStore(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) DashboardPeriodPreset preset
    ) {
        return ResponseEntity.ok(platformDashboardService.commissionByStore(from, to, preset));
    }

    @GetMapping("/sales-by-day")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<PlatformSalesByDayResponse>> salesByDay(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) DashboardPeriodPreset preset
    ) {
        return ResponseEntity.ok(platformDashboardService.salesByDay(from, to, preset));
    }

    @GetMapping("/top-stores")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<PlatformTopStoreResponse>> topStores(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) DashboardPeriodPreset preset
    ) {
        return ResponseEntity.ok(platformDashboardService.topStores(from, to, preset));
    }
}
