package com.planno.store.payment.account.service;

import com.planno.store.core.exception.BusinessException;
import com.planno.store.core.exception.NotFoundException;
import com.planno.store.core.security.AuthenticatedUser;
import com.planno.store.payment.account.domain.PaymentProviderOAuthState;
import com.planno.store.payment.account.dto.OAuthCallbackResponse;
import com.planno.store.payment.account.dto.OAuthConnectionResponse;
import com.planno.store.payment.account.repository.PaymentProviderOAuthStateRepository;
import com.planno.store.payment.config.PaymentProperties;
import com.planno.store.payment.domain.PaymentProvider;
import com.planno.store.store.service.CurrentStoreProvider;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class MercadoPagoOAuthService {

    private final PaymentProviderOAuthStateRepository oauthStateRepository;
    private final PaymentProviderAccountService paymentProviderAccountService;
    private final PaymentProperties paymentProperties;
    private final CurrentStoreProvider currentStoreProvider;
    private final MercadoPagoOAuthClient mercadoPagoOAuthClient;

    public MercadoPagoOAuthService(
            PaymentProviderOAuthStateRepository oauthStateRepository,
            PaymentProviderAccountService paymentProviderAccountService,
            PaymentProperties paymentProperties,
            CurrentStoreProvider currentStoreProvider,
            MercadoPagoOAuthClient mercadoPagoOAuthClient
    ) {
        this.oauthStateRepository = oauthStateRepository;
        this.paymentProviderAccountService = paymentProviderAccountService;
        this.paymentProperties = paymentProperties;
        this.currentStoreProvider = currentStoreProvider;
        this.mercadoPagoOAuthClient = mercadoPagoOAuthClient;
    }

    @Transactional
    public OAuthConnectionResponse startConnection() {
        AuthenticatedUser authenticatedUser = currentAuthenticatedUser();
        if (!authenticatedUser.canAccessStore(currentStoreProvider.getCurrentStoreId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "You do not have access to the requested store");
        }
        paymentProviderAccountService.getOrCreateCurrentStoreAccount();

        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        oauthStateRepository.save(PaymentProviderOAuthState.create(
                currentStoreProvider.getCurrentStore(),
                PaymentProvider.MERCADO_PAGO,
                state,
                authenticatedUser.getUserId(),
                OffsetDateTime.now().plusMinutes(paymentProperties.oauth().stateTtlMinutes())
        ));

        String authorizationUrl = paymentProperties.mercadoPago().authBaseUrl()
                + "/authorization?client_id=" + encode(paymentProperties.mercadoPago().clientId())
                + "&response_type=code"
                + "&platform_id=mp"
                + "&state=" + encode(state)
                + "&redirect_uri=" + encode(paymentProperties.mercadoPago().webhookUrl().replace("/webhooks/mercado-pago", "/payments/mercado-pago/oauth/callback"));

        return new OAuthConnectionResponse(PaymentProvider.MERCADO_PAGO.name(), authorizationUrl);
    }

    @Transactional
    public OAuthCallbackResponse handleCallback(String code, String state, String error) {
        if (StringUtils.hasText(error)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mercado Pago OAuth returned an error");
        }
        if (!StringUtils.hasText(code) || !StringUtils.hasText(state)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid Mercado Pago OAuth callback");
        }

        PaymentProviderOAuthState oauthState = oauthStateRepository.findByState(state)
                .orElseThrow(() -> new NotFoundException("OAuth state not found"));
        if (oauthState.isConsumed()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "OAuth state has already been consumed");
        }
        if (oauthState.isExpired()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "OAuth state has expired");
        }

        oauthState.consume();

        MercadoPagoOAuthTokenResponse tokenResponse = mercadoPagoOAuthClient.exchangeCode(
                code,
                paymentProperties.mercadoPago().webhookUrl().replace("/webhooks/mercado-pago", "/payments/mercado-pago/oauth/callback")
        );

        var account = paymentProviderAccountService.connectAccountForStore(oauthState.getStore().getId(), tokenResponse);
        return new OAuthCallbackResponse(
                account.getStore().getId(),
                account.getProvider().name(),
                account.getConnectionStatus().name(),
                account.hasValidAccessToken(),
                account.hasValidAccessToken(),
                account.hasValidAccessToken() && account.getPublicKey() != null
        );
    }

    private AuthenticatedUser currentAuthenticatedUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof AuthenticatedUser authenticatedUser) {
            return authenticatedUser;
        }
        throw new BusinessException(HttpStatus.UNAUTHORIZED, "Authentication is required");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
