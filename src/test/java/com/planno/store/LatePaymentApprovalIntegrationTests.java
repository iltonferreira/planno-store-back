package com.planno.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.planno.store.cart.domain.Cart;
import com.planno.store.catalog.domain.Product;
import com.planno.store.catalog.domain.ProductStatus;
import com.planno.store.identity.domain.RoleName;
import com.planno.store.inventory.domain.InventoryItem;
import com.planno.store.order.domain.Order;
import com.planno.store.order.domain.OrderItem;
import com.planno.store.order.domain.OrderStatus;
import com.planno.store.order.dto.UpdateOrderStatusRequest;
import com.planno.store.payment.account.domain.PaymentProviderAccount;
import com.planno.store.payment.domain.PaymentProvider;
import com.planno.store.payment.domain.PaymentStatus;
import com.planno.store.payment.dto.CreatePaymentRequest;
import com.planno.store.payment.refund.dto.CreateRefundRequest;
import com.planno.store.payment.service.StubMercadoPagoPaymentProviderClient;
import com.planno.store.payment.service.WebhookService;
import com.planno.store.store.domain.Store;
import com.planno.store.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Synthetic fixtures exercise cancellation and financial confirmation independently of provider HTTP.
class LatePaymentApprovalIntegrationTests extends AbstractIntegrationTest {
    @Autowired StubMercadoPagoPaymentProviderClient stub;
    @Autowired WebhookService webhook;

    private record Fixture(Store store, UUID productId, UUID orderId, String adminToken, String cartToken) {}

    private Fixture fixture(String slug) {
        Store store = createStore(slug);
        var admin = createUser(slug + "@example.com", "audit-fixture-password");
        assignRole(admin, RoleName.ADMIN, store);
        var product = productRepository.saveAndFlush(Product.create(store, null, "Audit product", "audit-product",
                "SKU-AUDIT", "Synthetic fixture", ProductStatus.ACTIVE, new BigDecimal("40.00"), null));
        inventoryItemRepository.saveAndFlush(InventoryItem.create(product, 8, 2));
        var cart = Cart.create(store, null, "audit-owner-" + UUID.randomUUID());
        cart.markCheckedOut();
        cart = cartRepository.saveAndFlush(cart);
        var order = Order.create(store, cart.getId(), "AUD-" + UUID.randomUUID().toString().substring(0, 12),
                OrderStatus.WAITING_PAYMENT, new BigDecimal("80.00"), new BigDecimal("12.90"), BigDecimal.ZERO,
                null, null, new BigDecimal("92.90"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("92.90"),
                "Synthetic customer", "customer@example.com", null, UUID.randomUUID(), "Synthetic shipping", "FIXED", 4);
        order.addItem(OrderItem.create(order, product, product.getName(), product.getSlug(), product.getSku(),
                2, new BigDecimal("40.00"), new BigDecimal("80.00")));
        order = orderRepository.saveAndFlush(order);
        addressRepository.saveAndFlush(com.planno.store.shipping.domain.Address.forOrder(store, cart.getId(), order,
                "Synthetic customer", "Synthetic street", "1", null, "Center", "City", "SP", "01001000", "BR"));
        var account = PaymentProviderAccount.create(store, PaymentProvider.MERCADO_PAGO);
        account.connect("audit-merchant-" + slug, "dummy-not-a-real-token", null, OffsetDateTime.now().plusDays(1),
                "dummy-public-key", "read write");
        paymentProviderAccountRepository.saveAndFlush(account);
        return new Fixture(store, product.getId(), order.getId(), tokenFor(admin.getEmail()), cart.getGuestToken());
    }

    private JsonNode initiate(Fixture fixture) throws Exception {
        String response = mockMvc.perform(post("/api/v1/public/payments")
                .header("X-Cart-Token", fixture.cartToken())
                .header(STORE_HEADER, fixture.store().getSlug())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreatePaymentRequest(fixture.orderId()))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private void approve(Fixture fixture, JsonNode initiation) {
        var account = paymentProviderAccountRepository.findByStoreIdAndProvider(fixture.store().getId(), PaymentProvider.MERCADO_PAGO).orElseThrow();
        String checkout = initiation.get("externalCheckoutId").asText();
        stub.simulatePaymentStatus(checkout, PaymentStatus.APPROVED, "pix");
        webhook.reconcilePaymentFromProvider(paymentRepository.findById(UUID.fromString(initiation.get("paymentId").asText())).orElseThrow(),
                stub.getPaymentDetails(account, checkout), "synthetic-audit");
    }

    @Test void approvalAfterCancellationMustHaveReconciliationPath() throws Exception {
        var f = fixture("audit-late-approval");
        var init = initiate(f);
        mockMvc.perform(patch("/api/v1/admin/orders/{id}/status", f.orderId())
                .header(STORE_HEADER, f.store().getSlug()).header("Authorization", "Bearer " + f.adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateOrderStatusRequest(OrderStatus.CANCELLED, "synthetic cancellation"))))
                .andExpect(status().isOk());
        assertEquals(0, inventoryItemRepository.findByProductIdAndProductStoreId(f.productId(), f.store().getId()).orElseThrow().getQuantityReserved());
        assertDoesNotThrow(() -> approve(f, init), "Late approval must be recorded without consuming released inventory");
        assertLatePaymentState(f, init);
        approve(f, init);
        assertLatePaymentState(f, init);
        var response = mockMvc.perform(post("/api/v1/admin/payments/{id}/reconcile", init.get("paymentId").asText())
                .header(STORE_HEADER, f.store().getSlug()).header("Authorization", "Bearer " + f.adminToken()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var result = objectMapper.readTree(response);
        assertFalse(result.get("changed").asBoolean());
        assertTrue(result.get("result").asText().contains("manual reconciliation"));
        assertEquals(0, refundRepository.count(), "Late approval must not issue an automatic refund");
    }

    private void assertLatePaymentState(Fixture f, JsonNode init) {
        assertEquals(OrderStatus.CANCELLED, orderRepository.findById(f.orderId()).orElseThrow().getStatus());
        assertEquals(PaymentStatus.APPROVED, paymentRepository.findById(UUID.fromString(init.get("paymentId").asText())).orElseThrow().getStatus());
        var inventory = inventoryItemRepository.findByProductIdAndProductStoreId(f.productId(), f.store().getId()).orElseThrow();
        assertEquals(8, inventory.getQuantityAvailable());
        assertEquals(0, inventory.getQuantityReserved());
    }

    @Test void webhookLateApprovalIsProcessedAndSignalsManualReconciliationOnReplay() throws Exception {
        var f = fixture("late-webhook");
        var init = initiate(f);
        mockMvc.perform(patch("/api/v1/admin/orders/{id}/status", f.orderId())
                .header(STORE_HEADER, f.store().getSlug()).header("Authorization", "Bearer " + f.adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateOrderStatusRequest(OrderStatus.CANCELLED, "Cancellation"))))
                .andExpect(status().isOk());
        String checkout = init.get("externalCheckoutId").asText();
        stub.simulatePaymentStatus(checkout, PaymentStatus.APPROVED, "pix");
        String payload = objectMapper.writeValueAsString(Map.of("type", "payment", "user_id", "audit-merchant-late-webhook",
                "data", Map.of("id", checkout)));
        for (String eventId : java.util.List.of("late-event-a", "late-event-a", "late-event-b")) {
            webhook.processMercadoPagoWebhook(payload, null, eventId, checkout);
            assertLatePaymentState(f, init);
        }
        var events = webhookEventRepository.findAll();
        assertEquals(2, events.size());
        webhook.reprocessStoredEvent(events.getFirst(), "synthetic-admin");
        assertLatePaymentState(f, init);
        for (var event : events) {
            assertTrue(event.isProcessed());
            assertTrue(event.getProcessingResult().contains("manual reconciliation"));
        }
        mockMvc.perform(post("/api/v1/admin/payments/{id}/refunds", init.get("paymentId").asText())
                .header(STORE_HEADER, f.store().getSlug()).header("Authorization", "Bearer " + f.adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateRefundRequest(new BigDecimal("92.90"), "No automatic policy"))))
                .andExpect(status().isBadRequest());
        assertEquals(0, refundRepository.count());
    }

    @Test void lateApprovalMustNotConsumeInventoryReservedForOtherOrders() throws Exception {
        var f = fixture("late-other-reserve");
        var init = initiate(f);
        mockMvc.perform(patch("/api/v1/admin/orders/{id}/status", f.orderId())
                .header(STORE_HEADER, f.store().getSlug()).header("Authorization", "Bearer " + f.adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateOrderStatusRequest(OrderStatus.CANCELLED, "Cancellation"))))
                .andExpect(status().isOk());
        var inventory = inventoryItemRepository.findByProductIdAndProductStoreId(f.productId(), f.store().getId()).orElseThrow();
        inventory.reserve(3);
        inventoryItemRepository.saveAndFlush(inventory);

        approve(f, init);
        approve(f, init);

        inventory = inventoryItemRepository.findByProductIdAndProductStoreId(f.productId(), f.store().getId()).orElseThrow();
        assertEquals(8, inventory.getQuantityAvailable());
        assertEquals(3, inventory.getQuantityReserved());
        assertEquals(OrderStatus.CANCELLED, orderRepository.findById(f.orderId()).orElseThrow().getStatus());
        assertEquals(PaymentStatus.APPROVED, paymentRepository.findById(UUID.fromString(init.get("paymentId").asText())).orElseThrow().getStatus());
        assertEquals(0, refundRepository.count());
    }

    @Test void externalFullRefundAfterLateApprovalKeepsCancellationAndSummaryReconcilesViaPayment() throws Exception {
        var f = fixture("late-external-refund");
        var init = initiate(f);
        mockMvc.perform(patch("/api/v1/admin/orders/{id}/status", f.orderId())
                .header(STORE_HEADER, f.store().getSlug()).header("Authorization", "Bearer " + f.adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateOrderStatusRequest(OrderStatus.CANCELLED, "Cancellation"))))
                .andExpect(status().isOk());
        approve(f, init);
        stub.simulatePaymentStatus(init.get("externalCheckoutId").asText(), PaymentStatus.REFUNDED, "pix");

        mockMvc.perform(post("/api/v1/admin/payments/{id}/reconcile", init.get("paymentId").asText())
                .header(STORE_HEADER, f.store().getSlug()).header("Authorization", "Bearer " + f.adminToken()))
                .andExpect(status().isOk());
        var summary = refundRepository.findAll().getFirst();
        assertTrue(summary.isProviderStateSummary());
        assertNull(summary.getProviderRefundId());
        var response = mockMvc.perform(post("/api/v1/admin/refunds/{id}/reconcile", summary.getId())
                .header(STORE_HEADER, f.store().getSlug()).header("Authorization", "Bearer " + f.adminToken()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertTrue(objectMapper.readTree(response).get("result").asText().contains("through payment"));
        assertEquals(OrderStatus.CANCELLED, orderRepository.findById(f.orderId()).orElseThrow().getStatus());
        assertEquals(PaymentStatus.REFUNDED, paymentRepository.findById(UUID.fromString(init.get("paymentId").asText())).orElseThrow().getStatus());
        var inventory = inventoryItemRepository.findByProductIdAndProductStoreId(f.productId(), f.store().getId()).orElseThrow();
        assertEquals(8, inventory.getQuantityAvailable());
        assertEquals(0, inventory.getQuantityReserved());
        assertEquals(1, refundRepository.count());
        assertEquals(new BigDecimal("92.90"), refundRepository.findById(summary.getId()).orElseThrow().getAmount());
    }
}
