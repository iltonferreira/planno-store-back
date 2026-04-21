package com.planno.store.pricing.coupon.service;

import com.planno.store.core.exception.BusinessException;
import com.planno.store.core.exception.ConflictException;
import com.planno.store.core.exception.NotFoundException;
import com.planno.store.pricing.coupon.domain.Coupon;
import com.planno.store.pricing.coupon.dto.CouponRequest;
import com.planno.store.pricing.coupon.dto.CouponResponse;
import com.planno.store.pricing.coupon.dto.CouponValidationResponse;
import com.planno.store.pricing.domain.DiscountType;
import com.planno.store.pricing.dto.AppliedCouponSummary;
import com.planno.store.store.service.CurrentStoreProvider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.planno.store.pricing.coupon.repository.CouponRepository;

@Service
public class CouponService {

    private final CouponRepository couponRepository;
    private final CurrentStoreProvider currentStoreProvider;

    public CouponService(CouponRepository couponRepository, CurrentStoreProvider currentStoreProvider) {
        this.couponRepository = couponRepository;
        this.currentStoreProvider = currentStoreProvider;
    }

    @Transactional(readOnly = true)
    public List<CouponResponse> listAdmin() {
        return couponRepository.findAllByStoreIdOrderByCreatedAtDesc(currentStoreProvider.getCurrentStoreId())
                .stream()
                .map(CouponResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CouponResponse getById(UUID couponId) {
        return CouponResponse.from(getEntity(couponId));
    }

    @Transactional
    public CouponResponse create(CouponRequest request) {
        validateRequest(request, null);
        Coupon coupon = Coupon.create(
                currentStoreProvider.getCurrentStore(),
                request.code(),
                request.description(),
                request.discountType(),
                request.discountValue(),
                request.minOrderAmount(),
                request.maxDiscountAmount(),
                request.startsAt(),
                request.endsAt(),
                request.maxUses(),
                request.active(),
                request.freeShipping()
        );
        return CouponResponse.from(couponRepository.save(coupon));
    }

    @Transactional
    public CouponResponse update(UUID couponId, CouponRequest request) {
        validateRequest(request, couponId);
        Coupon coupon = getEntity(couponId);
        coupon.update(
                request.code(),
                request.description(),
                request.discountType(),
                request.discountValue(),
                request.minOrderAmount(),
                request.maxDiscountAmount(),
                request.startsAt(),
                request.endsAt(),
                request.maxUses(),
                request.active(),
                request.freeShipping()
        );
        return CouponResponse.from(coupon);
    }

    @Transactional
    public void delete(UUID couponId) {
        couponRepository.delete(getEntity(couponId));
    }

    @Transactional(readOnly = true)
    public CouponValidationResponse validateForCurrentStore(String code, BigDecimal subtotalAfterAutomaticPromotions) {
        Coupon coupon = validateCoupon(code, subtotalAfterAutomaticPromotions);
        return new CouponValidationResponse(
                coupon.getId(),
                coupon.getCode(),
                coupon.getDescription(),
                coupon.getDiscountType(),
                coupon.getDiscountValue(),
                coupon.getMinOrderAmount(),
                coupon.getMaxDiscountAmount(),
                coupon.isFreeShipping()
        );
    }

    @Transactional(readOnly = true)
    public CouponBenefit resolveBenefit(UUID couponId, BigDecimal subtotalAfterAutomaticPromotions) {
        if (couponId == null) {
            return CouponBenefit.none();
        }
        Coupon coupon = getEntity(couponId);
        validateCoupon(coupon, subtotalAfterAutomaticPromotions, OffsetDateTime.now());

        BigDecimal discountAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (!coupon.isFreeShipping()) {
            discountAmount = switch (coupon.getDiscountType()) {
                case PERCENTAGE -> scale(subtotalAfterAutomaticPromotions.multiply(coupon.getDiscountValue())
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));
                case FIXED -> scale(coupon.getDiscountValue());
                case null -> BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            };
            if (coupon.getMaxDiscountAmount() != null && discountAmount.compareTo(coupon.getMaxDiscountAmount()) > 0) {
                discountAmount = coupon.getMaxDiscountAmount();
            }
            if (discountAmount.compareTo(subtotalAfterAutomaticPromotions) > 0) {
                discountAmount = subtotalAfterAutomaticPromotions;
            }
        }

        return new CouponBenefit(
                new AppliedCouponSummary(coupon.getId(), coupon.getCode(), coupon.isFreeShipping()),
                scale(discountAmount),
                coupon.isFreeShipping()
        );
    }

    @Transactional
    public Coupon consumeForOrder(UUID couponId, BigDecimal subtotalAfterAutomaticPromotions) {
        if (couponId == null) {
            return null;
        }
        Coupon coupon = couponRepository.findWithLockByIdAndStoreId(couponId, currentStoreProvider.getCurrentStoreId())
                .orElseThrow(() -> new NotFoundException("Coupon not found"));
        validateCoupon(coupon, subtotalAfterAutomaticPromotions, OffsetDateTime.now(), true);
        if (!coupon.hasAvailableUses()) {
            throw new BusinessException(HttpStatus.CONFLICT, "Coupon usage limit has been reached");
        }
        coupon.incrementUsage();
        return coupon;
    }

    @Transactional
    public void releaseUsage(UUID couponId) {
        if (couponId == null) {
            return;
        }
        Coupon coupon = couponRepository.findWithLockByIdAndStoreId(couponId, currentStoreProvider.getCurrentStoreId())
                .orElseThrow(() -> new NotFoundException("Coupon not found"));
        coupon.decrementUsage();
    }

    @Transactional(readOnly = true)
    public Coupon getOptionalEntity(UUID couponId) {
        if (couponId == null) {
            return null;
        }
        return getEntity(couponId);
    }

    private Coupon validateCoupon(String code, BigDecimal subtotalAfterAutomaticPromotions) {
        Coupon coupon = couponRepository.findByStoreIdAndCodeIgnoreCase(currentStoreProvider.getCurrentStoreId(), code.trim().toUpperCase())
                .orElseThrow(() -> new NotFoundException("Coupon not found"));
        validateCoupon(coupon, subtotalAfterAutomaticPromotions, OffsetDateTime.now());
        return coupon;
    }

    private void validateCoupon(Coupon coupon, BigDecimal subtotalAfterAutomaticPromotions, OffsetDateTime now) {
        validateCoupon(coupon, subtotalAfterAutomaticPromotions, now, true);
    }

    private void validateCoupon(Coupon coupon, BigDecimal subtotalAfterAutomaticPromotions, OffsetDateTime now, boolean checkMinimumAmount) {
        if (!coupon.isActiveAt(now)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Coupon is inactive or outside its validity period");
        }
        if (!coupon.hasAvailableUses()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Coupon usage limit has been reached");
        }
        if (checkMinimumAmount && coupon.getMinOrderAmount() != null && subtotalAfterAutomaticPromotions.compareTo(coupon.getMinOrderAmount()) < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Coupon minimum order amount was not reached");
        }
    }

    private Coupon getEntity(UUID couponId) {
        return couponRepository.findByIdAndStoreId(couponId, currentStoreProvider.getCurrentStoreId())
                .orElseThrow(() -> new NotFoundException("Coupon not found"));
    }

    private void validateRequest(CouponRequest request, UUID couponId) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Coupon end date must be after start date");
        }
        if (request.maxUses() != null && request.maxUses() <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Coupon maxUses must be greater than zero");
        }

        boolean codeExists = couponId == null
                ? couponRepository.existsByStoreIdAndCodeIgnoreCase(currentStoreProvider.getCurrentStoreId(), request.code())
                : couponRepository.existsByStoreIdAndCodeIgnoreCaseAndIdNot(currentStoreProvider.getCurrentStoreId(), request.code(), couponId);
        if (codeExists) {
            throw new ConflictException("Coupon code already exists for the current store");
        }

        if (request.freeShipping()) {
            if (request.discountType() != null || request.discountValue() != null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Free shipping coupons cannot define a monetary discount");
            }
        } else if (request.discountType() == null || request.discountValue() == null || request.discountValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Coupon must define a positive discount or enable free shipping");
        }
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public record CouponBenefit(
            AppliedCouponSummary appliedCoupon,
            BigDecimal discountAmount,
            boolean freeShipping
    ) {
        public static CouponBenefit none() {
            return new CouponBenefit(null, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), false);
        }
    }
}
