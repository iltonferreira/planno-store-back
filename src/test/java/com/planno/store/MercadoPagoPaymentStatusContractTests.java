package com.planno.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planno.store.core.exception.BusinessException;
import com.planno.store.order.domain.Order;
import com.planno.store.payment.account.domain.PaymentProviderAccount;
import com.planno.store.payment.account.service.PaymentProviderAccountService;
import com.planno.store.payment.config.PaymentProperties;
import com.planno.store.payment.domain.Payment;
import com.planno.store.payment.domain.PaymentStatus;
import com.planno.store.payment.service.MercadoPagoPaymentProviderClient;
import com.planno.store.payment.service.TransparentPaymentProviderRequest;
import com.planno.store.store.domain.Store;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MercadoPagoPaymentStatusContractTests {
    private static final String API = "https://payment-status.example.test";
    private final ObjectMapper mapper = new ObjectMapper();
    private MockRestServiceServer server;
    private MercadoPagoPaymentProviderClient client;
    private PaymentProviderAccount account;
    private Payment payment;

    @BeforeEach void setup() {
        var accounts = mock(PaymentProviderAccountService.class);
        account = mock(PaymentProviderAccount.class);
        when(accounts.decryptAccessToken(account)).thenReturn("synthetic-token");
        when(accounts.requireConnectedAccountForStore(any())).thenReturn(account);
        var properties = new PaymentProperties(null, null, new PaymentProperties.MercadoPagoProperties(
                false, null, null, null, null, "https://store.example.test/webhook", null, null, null, API, null, false));
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MercadoPagoPaymentProviderClient(builder, properties, mapper, accounts);
        payment = mock(Payment.class);
        var store = mock(Store.class);
        var order = mock(Order.class);
        when(payment.getStore()).thenReturn(store);
        when(store.getId()).thenReturn(UUID.randomUUID());
        when(payment.getOrder()).thenReturn(order);
        when(order.getId()).thenReturn(UUID.randomUUID());
        when(order.getCode()).thenReturn("STATUS-CONTRACT");
        when(payment.getId()).thenReturn(UUID.randomUUID());
        when(payment.getProviderMerchantId()).thenReturn("synthetic-merchant");
        when(payment.getIdempotencyKey()).thenReturn("synthetic-key");
        when(payment.getAmount()).thenReturn(new BigDecimal("92.90"));
        when(payment.getCommissionAmount()).thenReturn(BigDecimal.ZERO);
    }

    private String response(String status) throws Exception {
        return mapper.writeValueAsString(mapper.createObjectNode().put("id", "synthetic-payment")
                .put("status", status).put("transaction_amount", new BigDecimal("92.90")));
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"unrecognized", ""})
    void unknownLookupStatusCannotBecomeAnExpirableFailure(String status) throws Exception {
        server.expect(requestTo(API + "/v1/payments/synthetic-payment"))
                .andExpect(method(HttpMethod.GET)).andRespond(withSuccess(response(status), MediaType.APPLICATION_JSON));
        var error = assertThrows(BusinessException.class, () -> client.getPaymentDetails(account, "synthetic-payment"));
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatus());
        assertFalse(error.getMessage().contains("synthetic-token"));
        server.verify();
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"unrecognized", ""})
    void unknownCreationStatusCannotBecomeAnExpirableFailure(String status) throws Exception {
        server.expect(requestTo(API + "/v1/payments"))
                .andExpect(method(HttpMethod.POST)).andRespond(withSuccess(response(status), MediaType.APPLICATION_JSON));
        var request = new TransparentPaymentProviderRequest("synthetic-card-token", "visa", null, 1,
                "payer@example.com", null, null, null, null, null);
        var error = assertThrows(BusinessException.class, () -> client.createTransparentPayment(payment, request));
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatus());
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({"approved,APPROVED", "pending,PENDING", "in_process,PENDING", "authorized,PENDING",
            "rejected,REJECTED", "cancelled,CANCELLED", "refunded,REFUNDED", "charged_back,REFUNDED"})
    void knownProviderStatusesKeepExistingMeaning(String status, PaymentStatus expected) throws Exception {
        server.expect(requestTo(API + "/v1/payments/synthetic-payment"))
                .andRespond(withSuccess(response(status), MediaType.APPLICATION_JSON));
        assertEquals(expected, client.getPaymentDetails(account, "synthetic-payment").status());
        server.verify();
    }
}
