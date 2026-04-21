package com.planno.store;

import com.planno.store.catalog.domain.CategoryStatus;
import com.planno.store.catalog.domain.ProductStatus;
import com.planno.store.catalog.dto.CategoryRequest;
import com.planno.store.catalog.dto.ProductRequest;
import com.planno.store.identity.domain.RoleName;
import com.planno.store.identity.domain.User;
import com.planno.store.order.domain.OrderStatus;
import com.planno.store.order.dto.CreateOrderRequest;
import com.planno.store.order.dto.UpdateOrderStatusRequest;
import com.planno.store.pricing.coupon.domain.Coupon;
import com.planno.store.pricing.coupon.dto.CouponRequest;
import com.planno.store.pricing.coupon.dto.CouponValidationRequest;
import com.planno.store.pricing.domain.DiscountType;
import com.planno.store.pricing.promotion.domain.AppliesToType;
import com.planno.store.pricing.promotion.domain.PromotionType;
import com.planno.store.pricing.promotion.dto.PromotionRequest;
import com.planno.store.shipping.domain.ShippingMethodType;
import com.planno.store.shipping.dto.AddressRequest;
import com.planno.store.shipping.dto.ShippingMethodRequest;
import com.planno.store.store.domain.Store;
import com.planno.store.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PromotionsCouponsIntegrationTests extends AbstractIntegrationTest {

    private static final String CART_HEADER = "X-Cart-Token";

    @Test
    void automaticPromotionsShouldApplyByProductAndCategoryAndIgnoreInactiveOrExpiredRules() throws Exception {
        AdminContext admin = createAdminContext("promo-store", "promo-admin@example.com");
        String categoryId = createCategory(admin, "Categoria Promo", "categoria-promo");
        String productA = createProduct(admin, "produto-promo-a", "SKU-PROMO-A", "100.00", categoryId);
        String productB = createProduct(admin, "produto-promo-b", "SKU-PROMO-B", "50.00", categoryId);
        updateInventory(admin, productA, 20);
        updateInventory(admin, productB, 20);

        createPromotion(admin, new PromotionRequest(
                "Produto 10%",
                null,
                PromotionType.PRODUCT,
                DiscountType.PERCENTAGE,
                new BigDecimal("10.00"),
                AppliesToType.PRODUCT,
                UUID.fromString(productA),
                null,
                null,
                OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now().plusDays(5),
                true
        ));
        createPromotion(admin, new PromotionRequest(
                "Categoria 5",
                null,
                PromotionType.CATEGORY,
                DiscountType.FIXED,
                new BigDecimal("5.00"),
                AppliesToType.CATEGORY,
                UUID.fromString(categoryId),
                null,
                null,
                OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now().plusDays(5),
                true
        ));
        createPromotion(admin, new PromotionRequest(
                "Inativa",
                null,
                PromotionType.PRODUCT,
                DiscountType.PERCENTAGE,
                new BigDecimal("90.00"),
                AppliesToType.PRODUCT,
                UUID.fromString(productA),
                null,
                null,
                OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now().plusDays(1),
                false
        ));
        createPromotion(admin, new PromotionRequest(
                "Expirada",
                null,
                PromotionType.PRODUCT,
                DiscountType.PERCENTAGE,
                new BigDecimal("80.00"),
                AppliesToType.PRODUCT,
                UUID.fromString(productA),
                null,
                null,
                OffsetDateTime.now().minusDays(10),
                OffsetDateTime.now().minusDays(5),
                true
        ));

        String cartTokenA = createCart(admin.storeSlug());
        addCartItem(admin.storeSlug(), cartTokenA, productA, 2);

        mockMvc.perform(get("/api/v1/public/cart")
                        .header(STORE_HEADER, admin.storeSlug())
                        .header(CART_HEADER, cartTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotalBeforeDiscount").value(200.00))
                .andExpect(jsonPath("$.itemDiscountAmount").value(20.00))
                .andExpect(jsonPath("$.discountAmount").value(20.00))
                .andExpect(jsonPath("$.subtotal").value(180.00))
                .andExpect(jsonPath("$.items[0].unitFinalPrice").value(90.00));

        String cartTokenB = createCart(admin.storeSlug());
        addCartItem(admin.storeSlug(), cartTokenB, productB, 1);

        mockMvc.perform(get("/api/v1/public/cart")
                        .header(STORE_HEADER, admin.storeSlug())
                        .header(CART_HEADER, cartTokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotalBeforeDiscount").value(50.00))
                .andExpect(jsonPath("$.itemDiscountAmount").value(5.00))
                .andExpect(jsonPath("$.subtotal").value(45.00))
                .andExpect(jsonPath("$.items[0].unitFinalPrice").value(45.00));
    }

    @Test
    void couponsShouldValidateApplyAndRemoveWithStoreIsolation() throws Exception {
        AdminContext admin = createAdminContext("coupon-store", "coupon-admin@example.com");
        String productId = createProduct(admin, "produto-cupom", "SKU-COUPON", "100.00", null);
        updateInventory(admin, productId, 20);

        createCoupon(admin, new CouponRequest(
                "OFF10",
                "Cupom valido",
                DiscountType.PERCENTAGE,
                new BigDecimal("10.00"),
                new BigDecimal("50.00"),
                null,
                OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now().plusDays(5),
                null,
                true,
                false
        ));
        createCoupon(admin, new CouponRequest(
                "EXPIRADO",
                "Expirado",
                DiscountType.FIXED,
                new BigDecimal("10.00"),
                null,
                null,
                OffsetDateTime.now().minusDays(10),
                OffsetDateTime.now().minusDays(1),
                null,
                true,
                false
        ));
        createCoupon(admin, new CouponRequest(
                "INATIVO",
                "Inativo",
                DiscountType.FIXED,
                new BigDecimal("10.00"),
                null,
                null,
                OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now().plusDays(2),
                null,
                false,
                false
        ));
        String limitedCouponId = createCoupon(admin, new CouponRequest(
                "LIMITE",
                "Limite",
                DiscountType.FIXED,
                new BigDecimal("10.00"),
                null,
                null,
                OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now().plusDays(2),
                1,
                true,
                false
        ));
        Coupon limitedCoupon = couponRepository.findById(UUID.fromString(limitedCouponId)).orElseThrow();
        limitedCoupon.incrementUsage();
        couponRepository.saveAndFlush(limitedCoupon);

        createCoupon(admin, new CouponRequest(
                "MIN150",
                "Minimo alto",
                DiscountType.FIXED,
                new BigDecimal("10.00"),
                new BigDecimal("150.00"),
                null,
                OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now().plusDays(2),
                null,
                true,
                false
        ));

        AdminContext otherAdmin = createAdminContext("other-store", "other-admin@example.com");
        createCoupon(otherAdmin, new CouponRequest(
                "OTHER10",
                "Outra store",
                DiscountType.FIXED,
                new BigDecimal("10.00"),
                null,
                null,
                OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now().plusDays(2),
                null,
                true,
                false
        ));

        String cartToken = createCart(admin.storeSlug());
        addCartItem(admin.storeSlug(), cartToken, productId, 1);

        mockMvc.perform(post("/api/v1/public/coupons/validate")
                        .header(STORE_HEADER, admin.storeSlug())
                        .header(CART_HEADER, cartToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CouponValidationRequest("OFF10"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OFF10"));

        assertCouponValidationError(admin, cartToken, "EXPIRADO");
        assertCouponValidationError(admin, cartToken, "INATIVO");
        assertCouponValidationError(admin, cartToken, "LIMITE");
        assertCouponValidationError(admin, cartToken, "MIN150");
        assertCouponValidationError(admin, cartToken, "OTHER10");

        mockMvc.perform(post("/api/v1/public/cart/coupon")
                        .header(STORE_HEADER, admin.storeSlug())
                        .header(CART_HEADER, cartToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CouponValidationRequest("OFF10"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.couponCode").value("OFF10"))
                .andExpect(jsonPath("$.couponDiscountAmount").value(10.00))
                .andExpect(jsonPath("$.discountAmount").value(10.00))
                .andExpect(jsonPath("$.total").value(90.00));

        mockMvc.perform(delete("/api/v1/public/cart/coupon")
                        .header(STORE_HEADER, admin.storeSlug())
                        .header(CART_HEADER, cartToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.couponCode").doesNotExist())
                .andExpect(jsonPath("$.couponDiscountAmount").value(0.00))
                .andExpect(jsonPath("$.total").value(100.00));
    }

    @Test
    void orderShouldPersistDiscountSnapshotConsumeAndReleaseCouponAndHideCommissionFromStoreAdmin() throws Exception {
        AdminContext admin = createAdminContext("order-promo-store", "order-promo-admin@example.com");
        String productId = createProduct(admin, "produto-order", "SKU-ORDER", "100.00", null);
        updateInventory(admin, productId, 20);
        String shippingMethodId = createShippingMethod(admin, "Entrega", "10.00");

        createPromotion(admin, new PromotionRequest(
                "Produto 10%",
                null,
                PromotionType.PRODUCT,
                DiscountType.PERCENTAGE,
                new BigDecimal("10.00"),
                AppliesToType.PRODUCT,
                UUID.fromString(productId),
                null,
                null,
                OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now().plusDays(5),
                true
        ));

        String couponId = createCoupon(admin, new CouponRequest(
                "FRETEGRATIS",
                "Frete gratis",
                null,
                null,
                null,
                null,
                OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now().plusDays(5),
                5,
                true,
                true
        ));

        String cartToken = createCart(admin.storeSlug());
        addCartItem(admin.storeSlug(), cartToken, productId, 2);

        mockMvc.perform(post("/api/v1/public/cart/coupon")
                        .header(STORE_HEADER, admin.storeSlug())
                        .header(CART_HEADER, cartToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CouponValidationRequest("FRETEGRATIS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.freeShippingApplied").value(true))
                .andExpect(jsonPath("$.total").value(180.00));

        UUID cartId = currentCartId(admin.storeSlug(), cartToken);
        String orderResponse = mockMvc.perform(post("/api/v1/public/orders")
                        .header(STORE_HEADER, admin.storeSlug())
                        .header(CART_HEADER, cartToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateOrderRequest(
                                cartId,
                                UUID.fromString(shippingMethodId),
                                "Cliente Promo",
                                "cliente@example.com",
                                "12345678900",
                                sampleAddress()
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.discountAmount").value(30.00))
                .andExpect(jsonPath("$.shippingAmount").value(10.00))
                .andExpect(jsonPath("$.totalAmount").value(180.00))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID orderId = UUID.fromString(objectMapper.readTree(orderResponse).get("id").asText());
        var order = orderRepository.findById(orderId).orElseThrow();
        Assertions.assertEquals(new BigDecimal("30.00"), order.getDiscountAmount());
        Assertions.assertEquals("FRETEGRATIS", order.getCouponCodeSnapshot());
        Assertions.assertEquals(new BigDecimal("180.00"), order.getTotalAmount());
        Assertions.assertEquals(new BigDecimal("4.50"), order.getCommissionAmount());

        Coupon coupon = couponRepository.findById(UUID.fromString(couponId)).orElseThrow();
        Assertions.assertEquals(1, coupon.getCurrentUses());

        mockMvc.perform(get("/api/v1/admin/orders/{id}", orderId)
                        .header(STORE_HEADER, admin.storeSlug())
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("commissionAmount"))))
                .andExpect(content().string(not(containsString("commissionRate"))))
                .andExpect(content().string(not(containsString("sellerNetAmount"))));

        mockMvc.perform(patch("/api/v1/admin/orders/{id}/status", orderId)
                        .header(STORE_HEADER, admin.storeSlug())
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateOrderStatusRequest(
                                OrderStatus.CANCELLED,
                                "Cancelado para liberar cupom"
                        ))))
                .andExpect(status().isOk());

        coupon = couponRepository.findById(UUID.fromString(couponId)).orElseThrow();
        Assertions.assertEquals(0, coupon.getCurrentUses());
    }

    @Test
    void storeIsolationAndAdminScopeShouldProtectPromotionsAndCoupons() throws Exception {
        AdminContext storeA = createAdminContext("admin-a-store", "admin-a-phase8@example.com");
        AdminContext storeB = createAdminContext("admin-b-store", "admin-b-phase8@example.com");

        String promoId = createPromotion(storeB, new PromotionRequest(
                "Promo B",
                null,
                PromotionType.ORDER,
                DiscountType.FIXED,
                new BigDecimal("10.00"),
                AppliesToType.ORDER,
                null,
                null,
                null,
                OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now().plusDays(2),
                true
        ));

        mockMvc.perform(get("/api/v1/admin/promotions/{id}", UUID.fromString(promoId))
                        .header(STORE_HEADER, storeA.storeSlug())
                        .header("Authorization", "Bearer " + storeA.token()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/admin/coupons")
                        .header(STORE_HEADER, storeB.storeSlug())
                        .header("Authorization", "Bearer " + storeA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CouponRequest(
                                "NAOPODE",
                                null,
                                DiscountType.FIXED,
                                new BigDecimal("10.00"),
                                null,
                                null,
                                OffsetDateTime.now().minusDays(1),
                                OffsetDateTime.now().plusDays(2),
                                null,
                                true,
                                false
                        ))))
                .andExpect(status().isForbidden());
    }

    private void assertCouponValidationError(AdminContext admin, String cartToken, String code) throws Exception {
        mockMvc.perform(post("/api/v1/public/coupons/validate")
                        .header(STORE_HEADER, admin.storeSlug())
                        .header(CART_HEADER, cartToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CouponValidationRequest(code))))
                .andExpect(status().is4xxClientError());
    }

    private AdminContext createAdminContext(String storeSlug, String adminEmail) {
        Store store = storeRepository.findBySlugIgnoreCase(storeSlug).orElseGet(() -> createStore(storeSlug));
        User admin = userRepository.findByEmailIgnoreCase(adminEmail).orElseGet(() -> createUser(adminEmail, "password123"));
        if (assignmentRepository.findAll().stream().noneMatch(it ->
                it.getUser().getId().equals(admin.getId())
                        && it.getStore() != null
                        && it.getStore().getId().equals(store.getId())
                        && it.getRole().getName() == RoleName.ADMIN)) {
            assignRole(admin, RoleName.ADMIN, store);
        }
        return new AdminContext(storeSlug, tokenFor(admin.getEmail()));
    }

    private String createCategory(AdminContext admin, String name, String slug) throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/categories")
                        .header(STORE_HEADER, admin.storeSlug())
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest(
                                name,
                                slug,
                                "Categoria de teste",
                                CategoryStatus.ACTIVE
                        ))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private String createProduct(AdminContext admin, String slug, String sku, String price, String categoryId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/products")
                        .header(STORE_HEADER, admin.storeSlug())
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductRequest(
                                categoryId == null ? null : UUID.fromString(categoryId),
                                "Produto " + slug,
                                slug,
                                sku,
                                "Descricao",
                                ProductStatus.ACTIVE,
                                new BigDecimal(price),
                                null
                        ))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private void updateInventory(AdminContext admin, String productId, int quantityAvailable) throws Exception {
        mockMvc.perform(put("/api/v1/admin/inventory/{productId}", UUID.fromString(productId))
                        .header(STORE_HEADER, admin.storeSlug())
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantityAvailable\":" + quantityAvailable + ",\"quantityReserved\":0}"))
                .andExpect(status().isOk());
    }

    private String createPromotion(AdminContext admin, PromotionRequest request) throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/promotions")
                        .header(STORE_HEADER, admin.storeSlug())
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private String createCoupon(AdminContext admin, CouponRequest request) throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/coupons")
                        .header(STORE_HEADER, admin.storeSlug())
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private String createShippingMethod(AdminContext admin, String name, String price) throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/shipping/methods")
                        .header(STORE_HEADER, admin.storeSlug())
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ShippingMethodRequest(
                                name,
                                ShippingMethodType.FIXED,
                                new BigDecimal(price),
                                4,
                                true
                        ))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private String createCart(String storeSlug) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/public/cart")
                        .header(STORE_HEADER, storeSlug))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString()).get("cartToken").asText();
    }

    private void addCartItem(String storeSlug, String cartToken, String productId, int quantity) throws Exception {
        mockMvc.perform(post("/api/v1/public/cart/items")
                        .header(STORE_HEADER, storeSlug)
                        .header(CART_HEADER, cartToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk());
    }

    private UUID currentCartId(String storeSlug, String cartToken) throws Exception {
        String response = mockMvc.perform(get("/api/v1/public/cart")
                        .header(STORE_HEADER, storeSlug)
                        .header(CART_HEADER, cartToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private AddressRequest sampleAddress() {
        return new AddressRequest(
                "Cliente Teste",
                "Rua Principal",
                "100",
                "Apto 10",
                "Centro",
                "Sao Paulo",
                "SP",
                "01001000",
                "BR"
        );
    }

    private record AdminContext(String storeSlug, String token) {
    }
}
