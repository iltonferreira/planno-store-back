package com.planno.store.cart.service;

import com.planno.store.cart.domain.Cart;
import com.planno.store.cart.domain.CartItem;
import com.planno.store.cart.domain.CartStatus;
import com.planno.store.cart.dto.CartItemRequest;
import com.planno.store.cart.dto.CartResponse;
import com.planno.store.cart.dto.CreateCartResponse;
import com.planno.store.cart.dto.UpdateCartItemRequest;
import com.planno.store.pricing.coupon.dto.CouponValidationResponse;
import com.planno.store.cart.repository.CartItemRepository;
import com.planno.store.cart.repository.CartRepository;
import com.planno.store.catalog.domain.Product;
import com.planno.store.catalog.domain.ProductStatus;
import com.planno.store.catalog.service.ProductService;
import com.planno.store.core.exception.BusinessException;
import com.planno.store.core.exception.NotFoundException;
import com.planno.store.inventory.service.InventoryService;
import com.planno.store.pricing.dto.PriceCalculation;
import com.planno.store.pricing.dto.CartPricingSummary;
import com.planno.store.pricing.coupon.service.CouponService;
import com.planno.store.pricing.service.PricingService;
import com.planno.store.store.service.CurrentStoreProvider;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CurrentStoreProvider currentStoreProvider;
    private final ProductService productService;
    private final PricingService pricingService;
    private final CouponService couponService;
    private final InventoryService inventoryService;
    private final CartTokenProvider cartTokenProvider;

    public CartService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            CurrentStoreProvider currentStoreProvider,
            ProductService productService,
            PricingService pricingService,
            CouponService couponService,
            InventoryService inventoryService,
            CartTokenProvider cartTokenProvider
    ) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.currentStoreProvider = currentStoreProvider;
        this.productService = productService;
        this.pricingService = pricingService;
        this.couponService = couponService;
        this.inventoryService = inventoryService;
        this.cartTokenProvider = cartTokenProvider;
    }

    @Transactional
    public CreateCartResponse createCart() {
        Cart cart = Cart.create(
                currentStoreProvider.getCurrentStore(),
                null,
                UUID.randomUUID().toString()
        );
        Cart saved = cartRepository.save(cart);
        return new CreateCartResponse(saved.getGuestToken(), CartResponse.from(saved));
    }

    @Transactional(readOnly = true)
    public CartResponse getCurrentCart() {
        return CartResponse.from(getCurrentActiveCart());
    }

    @Transactional
    public CartResponse addItem(CartItemRequest request) {
        Cart cart = getCurrentActiveCart();
        Product product = getSellableProduct(request.productId());
        inventoryService.assertAvailableQuantity(product.getId(), request.quantity());

        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())
                .orElse(null);

        int newQuantity = request.quantity();
        if (item != null) {
            newQuantity = item.getQuantity() + request.quantity();
            inventoryService.assertAvailableQuantity(product.getId(), newQuantity);
            PriceCalculation calculation = pricingService.calculatePrice(product.getId(), newQuantity);
            item.applyPricing(newQuantity, calculation);
        } else {
            PriceCalculation calculation = pricingService.calculatePrice(product.getId(), request.quantity());
            item = CartItem.create(cart, product, request.quantity(), calculation);
            cart.addItem(item);
        }

        recalculateCart(cart);
        return CartResponse.from(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse updateItem(UUID itemId, UpdateCartItemRequest request) {
        Cart cart = getCurrentActiveCart();
        CartItem item = getCurrentCartItem(itemId);
        inventoryService.assertAvailableQuantity(item.getProduct().getId(), request.quantity());

        PriceCalculation calculation = pricingService.calculatePrice(item.getProduct().getId(), request.quantity());
        item.applyPricing(request.quantity(), calculation);
        recalculateCart(cart);
        return CartResponse.from(cartRepository.save(cart));
    }

    @Transactional
    public void removeItem(UUID itemId) {
        Cart cart = getCurrentActiveCart();
        CartItem item = getCurrentCartItem(itemId);
        cart.removeItem(item);
        recalculateCart(cart);
        cartRepository.save(cart);
    }

    @Transactional
    public CartResponse clearCart() {
        Cart cart = getCurrentActiveCart();
        cart.clearItems();
        recalculateCart(cart);
        return CartResponse.from(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse applyCoupon(String code) {
        Cart cart = getCurrentActiveCart();
        CouponValidationResponse coupon = couponService.validateForCurrentStore(code, currentSubtotalAfterAutomaticPromotions());
        cart.applyCoupon(coupon.couponId(), coupon.code());
        recalculateCart(cart);
        return CartResponse.from(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse removeCoupon() {
        Cart cart = getCurrentActiveCart();
        cart.removeCoupon();
        recalculateCart(cart);
        return CartResponse.from(cartRepository.save(cart));
    }

    @Transactional(readOnly = true)
    public BigDecimal currentSubtotalAfterAutomaticPromotions() {
        return pricingService.currentSubtotalAfterAutomaticPromotions(getCurrentActiveCart());
    }

    @Transactional(readOnly = true)
    public Cart getCurrentActiveCart() {
        UUID storeId = currentStoreProvider.getCurrentStoreId();
        String token = cartTokenProvider.requireCurrentToken();
        return cartRepository.findByStoreIdAndGuestTokenAndStatus(storeId, token, CartStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Active cart not found for the current store"));
    }

    @Transactional
    public Cart getCurrentCartForCheckout(UUID cartId) {
        UUID storeId = currentStoreProvider.getCurrentStoreId();
        String token = cartTokenProvider.requireCurrentToken();
        return cartRepository.findForCheckoutUpdate(cartId, storeId, token)
                .orElseThrow(() -> new NotFoundException("Cart not found for the current store"));
    }

    @Transactional(readOnly = true)
    public Cart getCurrentCheckedOutCart() {
        UUID storeId = currentStoreProvider.getCurrentStoreId();
        String token = cartTokenProvider.requireCurrentToken();
        return cartRepository.findByStoreIdAndGuestTokenAndStatus(storeId, token, CartStatus.CHECKED_OUT)
                .orElseThrow(() -> new NotFoundException("Cart not found for the current store"));
    }

    private CartItem getCurrentCartItem(UUID itemId) {
        UUID storeId = currentStoreProvider.getCurrentStoreId();
        String token = cartTokenProvider.requireCurrentToken();
        return cartItemRepository.findByIdAndCartStoreIdAndCartGuestTokenAndCartStatus(itemId, storeId, token, CartStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Cart item not found for the current store"));
    }

    private Product getSellableProduct(UUID productId) {
        Product product = productService.getProductEntity(productId);
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Inactive products cannot be added to cart");
        }
        return product;
    }

    private void recalculateCart(Cart cart) {
        CartPricingSummary summary = pricingService.calculateCartPricing(cart);
        cart.applyPricing(
                summary.subtotalBeforeDiscount(),
                summary.itemDiscountAmount(),
                summary.couponDiscountAmount(),
                summary.discountAmount(),
                summary.subtotalAfterDiscount(),
                summary.totalAmount(),
                summary.freeShippingApplied()
        );
    }
}
