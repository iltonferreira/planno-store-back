package com.planno.store.shipping.controller;

import com.planno.store.shipping.dto.ShippingQuoteRequest;
import com.planno.store.shipping.dto.ShippingQuoteResponse;
import com.planno.store.shipping.service.ShippingService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/shipping")
public class PublicShippingController {

    private final ShippingService shippingService;

    public PublicShippingController(ShippingService shippingService) {
        this.shippingService = shippingService;
    }

    @PostMapping("/quote")
    public ResponseEntity<List<ShippingQuoteResponse>> quote(@Valid @RequestBody ShippingQuoteRequest request) {
        return ResponseEntity.ok(shippingService.quote(request));
    }
}

