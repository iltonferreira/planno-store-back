package com.planno.store.pricing.promotion.service;

import com.planno.store.catalog.domain.Product;
import com.planno.store.core.exception.BusinessException;
import com.planno.store.core.exception.NotFoundException;
import com.planno.store.pricing.domain.DiscountType;
import com.planno.store.pricing.dto.AppliedPromotionSummary;
import com.planno.store.pricing.promotion.domain.AppliesToType;
import com.planno.store.pricing.promotion.domain.Promotion;
import com.planno.store.pricing.promotion.domain.PromotionType;
import com.planno.store.pricing.promotion.dto.PromotionRequest;
import com.planno.store.pricing.promotion.dto.PromotionResponse;
import com.planno.store.pricing.promotion.repository.PromotionRepository;
import com.planno.store.store.service.CurrentStoreProvider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromotionService {

    private final PromotionRepository promotionRepository;
    private final CurrentStoreProvider currentStoreProvider;

    public PromotionService(PromotionRepository promotionRepository, CurrentStoreProvider currentStoreProvider) {
        this.promotionRepository = promotionRepository;
        this.currentStoreProvider = currentStoreProvider;
    }

    @Transactional(readOnly = true)
    public List<PromotionResponse> listAdmin() {
        return promotionRepository.findAllByStoreIdOrderByCreatedAtDesc(currentStoreProvider.getCurrentStoreId())
                .stream()
                .map(PromotionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PromotionResponse getById(UUID promotionId) {
        return PromotionResponse.from(getEntity(promotionId));
    }

    @Transactional
    public PromotionResponse create(PromotionRequest request) {
        validateRequest(request);
        Promotion promotion = Promotion.create(
                currentStoreProvider.getCurrentStore(),
                request.name(),
                request.description(),
                request.type(),
                request.discountType(),
                request.discountValue(),
                request.appliesToType(),
                request.appliesToId(),
                request.minOrderAmount(),
                request.maxDiscountAmount(),
                request.startsAt(),
                request.endsAt(),
                request.active()
        );
        return PromotionResponse.from(promotionRepository.save(promotion));
    }

    @Transactional
    public PromotionResponse update(UUID promotionId, PromotionRequest request) {
        validateRequest(request);
        Promotion promotion = getEntity(promotionId);
        promotion.update(
                request.name(),
                request.description(),
                request.type(),
                request.discountType(),
                request.discountValue(),
                request.appliesToType(),
                request.appliesToId(),
                request.minOrderAmount(),
                request.maxDiscountAmount(),
                request.startsAt(),
                request.endsAt(),
                request.active()
        );
        return PromotionResponse.from(promotion);
    }

    @Transactional
    public void delete(UUID promotionId) {
        promotionRepository.delete(getEntity(promotionId));
    }

    @Transactional(readOnly = true)
    public PromotionDiscount findBestLinePromotion(Product product, int quantity) {
        BigDecimal lineBase = scale(product.getBasePrice().multiply(BigDecimal.valueOf(quantity)));
        OffsetDateTime now = OffsetDateTime.now();

        return activePromotions().stream()
                .filter(promotion -> promotion.getType() == PromotionType.PRODUCT || promotion.getType() == PromotionType.CATEGORY)
                .filter(promotion -> appliesToProduct(promotion, product))
                .filter(promotion -> meetsMinOrderAmount(promotion, lineBase))
                .map(promotion -> toDiscount(promotion, lineBase))
                .max(Comparator.comparing(PromotionDiscount::discountAmount))
                .orElse(PromotionDiscount.none());
    }

    @Transactional(readOnly = true)
    public PromotionDiscount findBestOrderPromotion(BigDecimal subtotalAfterItemPromotions) {
        return activePromotions().stream()
                .filter(promotion -> promotion.getType() == PromotionType.ORDER)
                .filter(promotion -> meetsMinOrderAmount(promotion, subtotalAfterItemPromotions))
                .map(promotion -> toDiscount(promotion, subtotalAfterItemPromotions))
                .max(Comparator.comparing(PromotionDiscount::discountAmount))
                .orElse(PromotionDiscount.none());
    }

    @Transactional(readOnly = true)
    public PromotionDiscount findFreeShippingPromotion(BigDecimal subtotalAfterDiscounts) {
        return activePromotions().stream()
                .filter(promotion -> promotion.getType() == PromotionType.FREE_SHIPPING)
                .filter(promotion -> meetsMinOrderAmount(promotion, subtotalAfterDiscounts))
                .findFirst()
                .map(promotion -> new PromotionDiscount(
                        AppliedPromotionSummaryFactory.from(promotion),
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        true
                ))
                .orElse(PromotionDiscount.none());
    }

    private List<Promotion> activePromotions() {
        OffsetDateTime now = OffsetDateTime.now();
        return promotionRepository.findAllByStoreIdAndActiveTrueAndStartsAtLessThanEqualAndEndsAtGreaterThanEqual(
                currentStoreProvider.getCurrentStoreId(),
                now,
                now
        );
    }

    private Promotion getEntity(UUID promotionId) {
        return promotionRepository.findByIdAndStoreId(promotionId, currentStoreProvider.getCurrentStoreId())
                .orElseThrow(() -> new NotFoundException("Promotion not found"));
    }

    private void validateRequest(PromotionRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Promotion end date must be after start date");
        }

        switch (request.type()) {
            case PRODUCT -> requireTarget(request, AppliesToType.PRODUCT);
            case CATEGORY -> requireTarget(request, AppliesToType.CATEGORY);
            case ORDER -> {
                if (request.appliesToType() != AppliesToType.ORDER) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "Order promotions must use appliesToType ORDER");
                }
                if (request.appliesToId() != null) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "Order promotions cannot target a specific entity");
                }
                requireMonetaryDiscount(request);
            }
            case FREE_SHIPPING -> {
                if (request.appliesToType() != AppliesToType.NONE || request.appliesToId() != null) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "Free shipping promotions cannot target a specific entity");
                }
                if (request.discountType() != null || request.discountValue() != null) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "Free shipping promotions cannot define a monetary discount");
                }
            }
        }
    }

    private void requireTarget(PromotionRequest request, AppliesToType expected) {
        if (request.appliesToType() != expected || request.appliesToId() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Promotion target is invalid for the selected type");
        }
        requireMonetaryDiscount(request);
    }

    private void requireMonetaryDiscount(PromotionRequest request) {
        if (request.discountType() == null || request.discountValue() == null || request.discountValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Promotion must define a positive discount");
        }
    }

    private boolean appliesToProduct(Promotion promotion, Product product) {
        return switch (promotion.getType()) {
            case PRODUCT -> promotion.getAppliesToId() != null && promotion.getAppliesToId().equals(product.getId());
            case CATEGORY -> promotion.getAppliesToId() != null
                    && product.getCategory() != null
                    && promotion.getAppliesToId().equals(product.getCategory().getId());
            default -> false;
        };
    }

    private boolean meetsMinOrderAmount(Promotion promotion, BigDecimal amount) {
        return promotion.getMinOrderAmount() == null || amount.compareTo(promotion.getMinOrderAmount()) >= 0;
    }

    private PromotionDiscount toDiscount(Promotion promotion, BigDecimal baseAmount) {
        BigDecimal discount = switch (promotion.getDiscountType()) {
            case PERCENTAGE -> scale(baseAmount.multiply(promotion.getDiscountValue()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));
            case FIXED -> scale(promotion.getDiscountValue());
            case null -> BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        };

        if (promotion.getMaxDiscountAmount() != null && discount.compareTo(promotion.getMaxDiscountAmount()) > 0) {
            discount = promotion.getMaxDiscountAmount();
        }
        if (discount.compareTo(baseAmount) > 0) {
            discount = baseAmount;
        }

        return new PromotionDiscount(AppliedPromotionSummaryFactory.from(promotion), scale(discount), false);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public record PromotionDiscount(
            AppliedPromotionSummary appliedPromotion,
            BigDecimal discountAmount,
            boolean freeShipping
    ) {
        public static PromotionDiscount none() {
            return new PromotionDiscount(null, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), false);
        }
    }

    private static final class AppliedPromotionSummaryFactory {
        private static AppliedPromotionSummary from(Promotion promotion) {
            return new AppliedPromotionSummary(promotion.getId(), promotion.getName(), promotion.getType());
        }
    }
}
