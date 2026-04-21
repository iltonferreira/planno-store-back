package com.planno.store.dashboard.store.controller;

import com.planno.store.core.time.DashboardPeriodPreset;
import com.planno.store.dashboard.store.dto.StoreDashboardSummaryResponse;
import com.planno.store.dashboard.store.dto.StoreRecentOrderResponse;
import com.planno.store.dashboard.store.dto.StoreSalesByDayResponse;
import com.planno.store.dashboard.store.dto.StoreTopProductResponse;
import com.planno.store.dashboard.store.service.StoreDashboardService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class AdminDashboardController {

    private final StoreDashboardService storeDashboardService;

    public AdminDashboardController(StoreDashboardService storeDashboardService) {
        this.storeDashboardService = storeDashboardService;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<StoreDashboardSummaryResponse> summary(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) DashboardPeriodPreset preset
    ) {
        return ResponseEntity.ok(storeDashboardService.summary(from, to, preset));
    }

    @GetMapping("/sales-by-day")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<List<StoreSalesByDayResponse>> salesByDay(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) DashboardPeriodPreset preset
    ) {
        return ResponseEntity.ok(storeDashboardService.salesByDay(from, to, preset));
    }

    @GetMapping("/top-products")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<List<StoreTopProductResponse>> topProducts(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) DashboardPeriodPreset preset
    ) {
        return ResponseEntity.ok(storeDashboardService.topProducts(from, to, preset));
    }

    @GetMapping("/recent-orders")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<List<StoreRecentOrderResponse>> recentOrders(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) DashboardPeriodPreset preset
    ) {
        return ResponseEntity.ok(storeDashboardService.recentOrders(from, to, preset));
    }
}
