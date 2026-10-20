package com.planno.store;

import com.planno.store.core.exception.BusinessException;
import com.planno.store.payment.config.PaymentProperties;
import com.planno.store.payment.service.MercadoPagoWebhookSignatureVerifier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Local cryptographic contract tests, with a synthetic secret; no provider HTTP or notification. */
class MercadoPagoWebhookSignatureContractTests {
    private static final String SECRET = "synthetic-webhook-secret";
    private static final String REQUEST_ID = "synthetic-request";
    private static final String DATA_ID = "abc123";
    // Independently computed HMAC-SHA256 vector for the documented manifest bytes.
    private static final String SIGNATURE = "ts=1700000000000,v1=50cba82eb5d831c5424f9780b1af86fe14a4e02e504baa6544612d2d7091517b";

    private MercadoPagoWebhookSignatureVerifier verifier(boolean mock, String secret) {
        return new MercadoPagoWebhookSignatureVerifier(new PaymentProperties(null, null,
                new PaymentProperties.MercadoPagoProperties(mock, null, null, null, secret,
                        null, null, null, null, "https://mercado-pago.example.test", null, false)));
    }

    @ParameterizedTest @ValueSource(strings = {"abc123", "ABC123", "AbC123"})
    void validDocumentedManifestNormalizesAlphanumericDataId(String dataId) {
        assertDoesNotThrow(() -> verifier(false, SECRET).verify(SIGNATURE, REQUEST_ID, dataId));
    }

    @Test void parserAcceptsDocumentedCommaSeparatedFieldsWithWhitespace() {
        assertDoesNotThrow(() -> verifier(false, SECRET).verify(
                "v1=50cba82eb5d831c5424f9780b1af86fe14a4e02e504baa6544612d2d7091517b, ts=1700000000000",
                REQUEST_ID, DATA_ID));
    }

    @Test void timestampIsIncludedVerbatimInTheSignedManifest() {
        assertDoesNotThrow(() -> verifier(false, SECRET).verify(
                "ts=1700000000,v1=1aa34401e1c7463d25746f905e03190aa48dea6ee7972d8e5ee23157e4c4ae65",
                REQUEST_ID, DATA_ID));
        unauthorized(SIGNATURE.replace("1700000000000", "1700000000"), REQUEST_ID, DATA_ID);
    }

    static Stream<Arguments> missingFields() {
        return Stream.of(
                Arguments.of(null, REQUEST_ID, DATA_ID), Arguments.of("", REQUEST_ID, DATA_ID),
                Arguments.of(" ", REQUEST_ID, DATA_ID), Arguments.of(SIGNATURE, null, DATA_ID),
                Arguments.of(SIGNATURE, "", DATA_ID), Arguments.of(SIGNATURE, " ", DATA_ID),
                Arguments.of(SIGNATURE, REQUEST_ID, null), Arguments.of(SIGNATURE, REQUEST_ID, ""),
                Arguments.of(SIGNATURE, REQUEST_ID, " "));
    }

    @ParameterizedTest @MethodSource("missingFields")
    void missingRequiredInputsFailClosed(String signature, String requestId, String dataId) {
        unauthorized(signature, requestId, dataId);
    }

    @ParameterizedTest @ValueSource(strings = {"ts=1700000000000", "v1=abc", "ts=,v1=abc",
            "ts=1700000000000,v1=", "ts:1700000000000,v1:abc", "unknown=x", "ts=1700000000000,v1=not-hex"})
    void malformedOrIncompleteSignatureFailsClosed(String signature) {
        unauthorized(signature, REQUEST_ID, DATA_ID);
    }

    @Test void changingAnySignedInputInvalidatesTheSignature() {
        unauthorized(SIGNATURE, "other-request", DATA_ID);
        unauthorized(SIGNATURE, REQUEST_ID, "other-data");
        unauthorized(SIGNATURE.replace("1700000000000", "1700000000001"), REQUEST_ID, DATA_ID);
        var error = assertThrows(BusinessException.class,
                () -> verifier(false, "another-synthetic-secret").verify(SIGNATURE, REQUEST_ID, DATA_ID));
        assertEquals(401, error.getStatus().value());
    }

    @Test void missingProductionModeSecretIsConfigurationFailure() {
        var error = assertThrows(BusinessException.class,
                () -> verifier(false, null).verify(SIGNATURE, REQUEST_ID, DATA_ID));
        assertEquals(500, error.getStatus().value());
    }

    @Test void mockBypassIsExplicitlyDifferentFromSignatureValidation() {
        assertDoesNotThrow(() -> verifier(true, null).verify(null, null, DATA_ID));
        var error = assertThrows(BusinessException.class, () -> verifier(true, null).verify(null, null, null));
        assertEquals(400, error.getStatus().value());
    }

    private void unauthorized(String signature, String requestId, String dataId) {
        var error = assertThrows(BusinessException.class,
                () -> verifier(false, SECRET).verify(signature, requestId, dataId));
        assertEquals(401, error.getStatus().value());
        assertFalse(error.getMessage().contains(SECRET));
        assertFalse(error.getMessage().contains(SIGNATURE));
    }
}
