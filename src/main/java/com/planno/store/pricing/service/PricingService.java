package com.planno.store.pricing.service;

import com.planno.store.catalog.domain.Product;
import com.planno.store.catalog.domain.ProductStatus;
import com.planno.store.catalog.service.ProductService;
import com.planno.store.cart.domain.Cart;
import com.planno.store.cart.domain.CartItem;
import com.planno.store.core.exception.BusinessException;
import com.planno.store.pricing.coupon.service.CouponService;
import com.planno.store.pricing.dto.PriceCalculation;
import com.planno.store.pricing.dto.CartPricingSummary;
import com.planno.store.pricing.promotion.service.PromotionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PricingService {

    private final ProductService productService;
    private final PromotionService promotionService;
    private final CouponService couponService;

    public PricingService(
            ProductService productService,
            PromotionService promotionService,
            CouponService couponService
    ) {
        this.productService = productService;
        this.promotionService = promotionService;
        this.couponService = couponService;
    }

    @Transactional(readOnly = true)
    public PriceCalculation calculatePrice(UUID productId, int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Quantity must be greater than zero");
        }

        Product product = productService.getProductEntity(productId);
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Inactive products cannot be priced for cart operations");
        }

        BigDecimal basePrice = scale(product.getBasePrice());
        PromotionService.PromotionDiscount promotionDiscount = promotionService.findBestLinePromotion(product, quantity);
        BigDecimal lineBasePrice = scale(basePrice.multiply(BigDecimal.valueOf(quantity)));
        BigDecimal lineDiscountAmount = promotionDiscount.discountAmount();
        BigDecimal discountAmount = scale(lineDiscountAmount.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP));
        BigDecimal finalPrice = scale(basePrice.subtract(discountAmount));
        BigDecimal lineFinalPrice = scale(finalPrice.multiply(BigDecimal.valueOf(quantity)));

        return new PriceCalculation(
                product.getId(),
                quantity,
                basePrice,
                finalPrice,
                discountAmount,
                promotionDiscount.appliedPromotion(),
                null,
                lineBasePrice,
                lineFinalPrice,
                lineDiscountAmount
        );
    }

    @Transactional(readOnly = true)
    public CartPricingSummary calculateCartPricing(Cart cart) {
        return calculateCartPricing(cart, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }

    @Transactional(readOnly = true)
    public CartPricingSummary calculateCartPricing(Cart cart, BigDecimal shippingAmount) {
        List<PriceCalculation> lineCalculations = cart.getItems().stream()
                .map(item -> calculatePrice(item.getProduct().getId(), item.getQuantity()))
                .toList();

        BigDecimal subtotalBeforeDiscount = lineCalculations.stream()
                .map(PriceCalculation::lineBasePrice)
                .reduce(zero(), BigDecimal::add);
        BigDecimal itemDiscountAmount = lineCalculations.stream()
                .map(PriceCalculation::lineDiscountAmount)
                .reduce(zero(), BigDecimal::add);
        BigDecimal subtotalAfterItemPromotions = subtotalBeforeDiscount.subtract(itemDiscountAmount);

        PromotionService.PromotionDiscount orderPromotion = promotionService.findBestOrderPromotion(scale(subtotalAfterItemPromotions));
        BigDecimal orderPromotionDiscountAmount = orderPromotion.discountAmount();
        BigDecimal subtotalAfterOrderPromotion = scale(subtotalAfterItemPromotions.subtract(orderPromotionDiscountAmount));

        CouponService.CouponBenefit couponBenefit = couponService.resolveBenefit(cart.getCouponId(), subtotalAfterOrderPromotion);
        BigDecimal couponDiscountAmount = couponBenefit.discountAmount();
        BigDecimal subtotalAfterDiscount = scale(subtotalAfterOrderPromotion.subtract(couponDiscountAmount));

        boolean freeShippingApplied = couponBenefit.freeShipping()
                || promotionService.findFreeShippingPromotion(subtotalAfterDiscount).freeShipping();
        BigDecimal shippingDiscountAmount = freeShippingApplied ? scale(shippingAmount) : zero();
        BigDecimal discountAmount = scale(itemDiscountAmount.add(orderPromotionDiscountAmount).add(couponDiscountAmount).add(shippingDiscountAmount));
        BigDecimal totalAmount = scale(subtotalAfterDiscount.add(scale(shippingAmount)).subtract(shippingDiscountAmount));

        return new CartPricingSummary(
                scale(subtotalBeforeDiscount),
                scale(subtotalAfterItemPromotions),
                subtotalAfterDiscount,
                scale(itemDiscountAmount),
                scale(orderPromotionDiscountAmount),
                scale(couponDiscountAmount),
                scale(shippingDiscountAmount),
                discountAmount,
                totalAmount,
                freeShippingApplied,
                orderPromotion.appliedPromotion(),
                couponBenefit.appliedCoupon()
        );
    }

    @Transactional(readOnly = true)
    public BigDecimal currentSubtotalAfterAutomaticPromotions(Cart cart) {
        return calculateCartPricing(cart).subtotalAfterItemPromotions();
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
