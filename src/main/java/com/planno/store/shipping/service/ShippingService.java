package com.planno.store.shipping.service;

import com.planno.store.cart.domain.Cart;
import com.planno.store.cart.service.CartService;
import com.planno.store.core.exception.BusinessException;
import com.planno.store.core.exception.NotFoundException;
import com.planno.store.shipping.domain.Address;
import com.planno.store.shipping.domain.ShippingMethod;
import com.planno.store.shipping.dto.AddressRequest;
import com.planno.store.shipping.dto.ShippingQuoteRequest;
import com.planno.store.shipping.dto.ShippingQuoteResponse;
import com.planno.store.shipping.repository.AddressRepository;
import com.planno.store.shipping.repository.ShippingMethodRepository;
import com.planno.store.store.service.CurrentStoreProvider;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShippingService {

    private final ShippingMethodRepository shippingMethodRepository;
    private final ShippingMethodService shippingMethodService;
    private final CurrentStoreProvider currentStoreProvider;
    private final CartService cartService;
    private final AddressRepository addressRepository;

    public ShippingService(
            ShippingMethodRepository shippingMethodRepository,
            ShippingMethodService shippingMethodService,
            CurrentStoreProvider currentStoreProvider,
            CartService cartService,
            AddressRepository addressRepository
    ) {
        this.shippingMethodRepository = shippingMethodRepository;
        this.shippingMethodService = shippingMethodService;
        this.currentStoreProvider = currentStoreProvider;
        this.cartService = cartService;
        this.addressRepository = addressRepository;
    }

    @Transactional(readOnly = true)
    public List<ShippingQuoteResponse> quote(ShippingQuoteRequest request) {
        Cart cart = cartService.getCurrentActiveCart();
        ensureCartMatchesRequest(cart, request.cartId());
        if (cart.getItems().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Cannot calculate shipping for an empty cart");
        }

        List<ShippingMethod> methods = shippingMethodRepository.findAllByStoreIdAndActiveTrueOrderByPriceAsc(currentStoreProvider.getCurrentStoreId());
        if (methods.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "No shipping method available for the current store");
        }

        validateAddress(request.address());
        return methods.stream().map(ShippingQuoteResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ShippingMethod getSelectedMethod(UUID cartId, UUID shippingMethodId, AddressRequest address) {
        Cart cart = cartService.getCurrentActiveCart();
        ensureCartMatchesRequest(cart, cartId);
        if (cart.getItems().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Cannot select shipping for an empty cart");
        }
        validateAddress(address);
        return shippingMethodService.getActiveMethod(shippingMethodId);
    }

    @Transactional
    public Address saveOrderAddress(UUID cartId, AddressRequest request, com.planno.store.order.domain.Order order) {
        return addressRepository.save(Address.forOrder(
                currentStoreProvider.getCurrentStore(),
                cartId,
                order,
                request.recipientName(),
                request.street(),
                request.number(),
                request.complement(),
                request.neighborhood(),
                request.city(),
                request.state(),
                request.postalCode(),
                request.country()
        ));
    }

    @Transactional(readOnly = true)
    public Address findOrderAddress(UUID orderId) {
        return addressRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("Order address not found"));
    }

    private void ensureCartMatchesRequest(Cart cart, UUID cartId) {
        if (!cart.getId().equals(cartId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Requested cart does not match the active cart context");
        }
    }

    private void validateAddress(AddressRequest address) {
        if (address == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Address is required for shipping quote");
        }
    }
}
