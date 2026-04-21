package com.planno.store.shipping.controller;

import com.planno.store.shipping.dto.ShippingMethodRequest;
import com.planno.store.shipping.dto.ShippingMethodResponse;
import com.planno.store.shipping.service.ShippingMethodService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/shipping/methods")
public class AdminShippingMethodController {

    private final ShippingMethodService shippingMethodService;

    public AdminShippingMethodController(ShippingMethodService shippingMethodService) {
        this.shippingMethodService = shippingMethodService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'PLATFORM_ADMIN')")
    public ResponseEntity<List<ShippingMethodResponse>> list() {
        return ResponseEntity.ok(shippingMethodService.listAdmin());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ShippingMethodResponse> create(@Valid @RequestBody ShippingMethodRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shippingMethodService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ShippingMethodResponse> update(@PathVariable UUID id, @Valid @RequestBody ShippingMethodRequest request) {
        return ResponseEntity.ok(shippingMethodService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        shippingMethodService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

