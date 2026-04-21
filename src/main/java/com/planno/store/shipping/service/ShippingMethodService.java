package com.planno.store.shipping.service;

import com.planno.store.core.exception.NotFoundException;
import com.planno.store.shipping.domain.ShippingMethod;
import com.planno.store.shipping.dto.ShippingMethodRequest;
import com.planno.store.shipping.dto.ShippingMethodResponse;
import com.planno.store.shipping.repository.ShippingMethodRepository;
import com.planno.store.store.service.CurrentStoreProvider;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShippingMethodService {

    private final ShippingMethodRepository shippingMethodRepository;
    private final CurrentStoreProvider currentStoreProvider;

    public ShippingMethodService(ShippingMethodRepository shippingMethodRepository, CurrentStoreProvider currentStoreProvider) {
        this.shippingMethodRepository = shippingMethodRepository;
        this.currentStoreProvider = currentStoreProvider;
    }

    @Transactional(readOnly = true)
    public List<ShippingMethodResponse> listAdmin() {
        return shippingMethodRepository.findAllByStoreIdOrderByNameAsc(currentStoreProvider.getCurrentStoreId())
                .stream()
                .map(ShippingMethodResponse::from)
                .toList();
    }

    @Transactional
    public ShippingMethodResponse create(ShippingMethodRequest request) {
        ShippingMethod method = ShippingMethod.create(
                currentStoreProvider.getCurrentStore(),
                request.name(),
                request.type(),
                request.price(),
                request.estimatedDays(),
                request.active()
        );
        return ShippingMethodResponse.from(shippingMethodRepository.save(method));
    }

    @Transactional
    public ShippingMethodResponse update(UUID id, ShippingMethodRequest request) {
        ShippingMethod method = getEntity(id);
        method.update(request.name(), request.type(), request.price(), request.estimatedDays(), request.active());
        return ShippingMethodResponse.from(method);
    }

    @Transactional
    public void delete(UUID id) {
        shippingMethodRepository.delete(getEntity(id));
    }

    @Transactional(readOnly = true)
    public ShippingMethod getActiveMethod(UUID id) {
        ShippingMethod method = getEntity(id);
        if (!method.isActive()) {
            throw new NotFoundException("Shipping method is not available");
        }
        return method;
    }

    private ShippingMethod getEntity(UUID id) {
        return shippingMethodRepository.findByIdAndStoreId(id, currentStoreProvider.getCurrentStoreId())
                .orElseThrow(() -> new NotFoundException("Shipping method not found"));
    }
}

