package com.smartelectro.smartelectro;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static com.smartelectro.smartelectro.CouponDtos.*;

@Service
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository redemptionRepository;

    public CouponService(CouponRepository couponRepository, CouponRedemptionRepository redemptionRepository) {
        this.couponRepository = couponRepository;
        this.redemptionRepository = redemptionRepository;
    }

    @Transactional(readOnly = true)
    public Evaluation evaluate(Long userId, String code, BigDecimal subtotal) {
        return evaluateCoupon(userId, code, subtotal, false);
    }

    @Transactional
    public Evaluation evaluateForCheckout(Long userId, String code, BigDecimal subtotal) {
        return evaluateCoupon(userId, code, subtotal, true);
    }

    @Transactional
    public void redeem(Coupon coupon, User user, CustomerOrder order, BigDecimal discount) {
        CouponRedemption redemption = new CouponRedemption();
        redemption.setCoupon(coupon);
        redemption.setUser(user);
        redemption.setOrder(order);
        redemption.setDiscountAmount(discount);
        redemptionRepository.save(redemption);
    }

    @Transactional(readOnly = true)
    public List<OfferView> activeOffers(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return couponRepository.findAllByOrderByIdDesc().stream()
                .filter(coupon -> coupon.isActive() && !now.isBefore(coupon.getStartsAt())
                        && !now.isAfter(coupon.getExpiresAt()))
                .filter(coupon -> coupon.getUsageLimit() == null
                        || redemptionRepository.countByCouponId(coupon.getId()) < coupon.getUsageLimit())
                .filter(coupon -> redemptionRepository.countByCouponIdAndUserId(coupon.getId(), userId)
                        < coupon.getPerCustomerUsageLimit())
                .map(coupon -> new OfferView(coupon.getCode(), coupon.getDiscountType(),
                        coupon.getDiscountValue(), coupon.getMinimumOrderValue(),
                        coupon.getMaximumDiscount(), coupon.getExpiresAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CouponAdminView> adminCoupons() {
        return couponRepository.findAllByOrderByIdDesc().stream().map(this::toAdminView).toList();
    }

    @Transactional
    public CouponAdminView create(CouponAdminRequest request) {
        validateRequest(request);
        String code = normalizeCode(request.code());
        if (couponRepository.findByCodeIgnoreCase(code).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A coupon with this code already exists.");
        }
        Coupon coupon = new Coupon();
        apply(coupon, request, code);
        try {
            return toAdminView(couponRepository.saveAndFlush(coupon));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A coupon with this code already exists.");
        }
    }

    @Transactional
    public CouponAdminView update(Long id, CouponAdminRequest request) {
        validateRequest(request);
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found."));
        String code = normalizeCode(request.code());
        couponRepository.findByCodeIgnoreCase(code).filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> { throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "A coupon with this code already exists."); });
        apply(coupon, request, code);
        try {
            return toAdminView(couponRepository.saveAndFlush(coupon));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A coupon with this code already exists.");
        }
    }

    @Transactional
    public CouponAdminView setActive(Long id, boolean active) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found."));
        coupon.setActive(active);
        return toAdminView(couponRepository.save(coupon));
    }

    @Transactional(readOnly = true)
    public List<CouponUsageView> usage(Long couponId) {
        if (!couponRepository.existsById(couponId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found.");
        }
        return redemptionRepository.findUsageByCouponId(couponId).stream()
                .map(redemption -> new CouponUsageView(maskEmail(redemption.getUser().getEmail()),
                        redemption.getOrder().getOrderNumber(), redemption.getDiscountAmount(),
                        redemption.getCreatedAt()))
                .toList();
    }

    private Evaluation evaluateCoupon(Long userId, String rawCode, BigDecimal subtotal, boolean lock) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a coupon code.");
        }
        String code = normalizeCode(rawCode);
        Coupon coupon = (lock ? couponRepository.findByCodeForUpdate(code)
                : couponRepository.findByCodeIgnoreCase(code))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "This coupon code is not valid."));
        validateEligibility(coupon, userId, subtotal);
        BigDecimal discount = coupon.getDiscountType() == CouponDiscountType.PERCENTAGE
                ? subtotal.multiply(coupon.getDiscountValue()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                : coupon.getDiscountValue().setScale(2, RoundingMode.HALF_UP);
        if (coupon.getMaximumDiscount() != null) {
            discount = discount.min(coupon.getMaximumDiscount());
        }
        discount = discount.min(subtotal).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        return new Evaluation(coupon, discount);
    }

    private void validateEligibility(Coupon coupon, Long userId, BigDecimal subtotal) {
        LocalDateTime now = LocalDateTime.now();
        if (!coupon.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This coupon is inactive.");
        }
        if (now.isBefore(coupon.getStartsAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This coupon is not active yet.");
        }
        if (now.isAfter(coupon.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This coupon has expired.");
        }
        if (subtotal.compareTo(coupon.getMinimumOrderValue()) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The order subtotal does not meet this coupon's minimum value.");
        }
        if (coupon.getUsageLimit() != null
                && redemptionRepository.countByCouponId(coupon.getId()) >= coupon.getUsageLimit()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This coupon has reached its usage limit.");
        }
        if (redemptionRepository.countByCouponIdAndUserId(coupon.getId(), userId)
                >= coupon.getPerCustomerUsageLimit()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You have reached the usage limit for this coupon.");
        }
    }

    private void validateRequest(CouponAdminRequest request) {
        if (request.startsAt() == null || request.expiresAt() == null
                || !request.expiresAt().isAfter(request.startsAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coupon expiry must be after its start date.");
        }
        if (request.discountType() == CouponDiscountType.PERCENTAGE
                && request.discountValue().compareTo(new BigDecimal("100")) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Percentage discounts cannot exceed 100%.");
        }
        if (request.discountType() == CouponDiscountType.FIXED_AMOUNT && request.maximumDiscount() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Maximum discount is only valid for percentage coupons.");
        }
    }

    private void apply(Coupon coupon, CouponAdminRequest request, String code) {
        coupon.setCode(code);
        coupon.setDiscountType(request.discountType());
        coupon.setDiscountValue(request.discountValue().setScale(2, RoundingMode.HALF_UP));
        coupon.setMinimumOrderValue(request.minimumOrderValue().setScale(2, RoundingMode.HALF_UP));
        coupon.setMaximumDiscount(request.maximumDiscount() == null ? null
                : request.maximumDiscount().setScale(2, RoundingMode.HALF_UP));
        coupon.setStartsAt(request.startsAt());
        coupon.setExpiresAt(request.expiresAt());
        coupon.setUsageLimit(request.usageLimit());
        coupon.setPerCustomerUsageLimit(request.perCustomerUsageLimit());
        coupon.setActive(request.active());
    }

    private CouponAdminView toAdminView(Coupon coupon) {
        return new CouponAdminView(coupon.getId(), coupon.getCode(), coupon.getDiscountType(),
                coupon.getDiscountValue(), coupon.getMinimumOrderValue(), coupon.getMaximumDiscount(),
                coupon.getStartsAt(), coupon.getExpiresAt(), coupon.getUsageLimit(),
                coupon.getPerCustomerUsageLimit(), coupon.isActive(),
                redemptionRepository.countByCouponId(coupon.getId()));
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String maskEmail(String email) {
        int separator = email.indexOf('@');
        return separator <= 0 ? "***" : email.substring(0, 1) + "***@" + email.substring(separator + 1);
    }

    public record Evaluation(Coupon coupon, BigDecimal discount) {
    }
}
