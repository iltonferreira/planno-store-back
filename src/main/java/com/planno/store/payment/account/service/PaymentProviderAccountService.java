package com.planno.store.payment.account.service;

import com.planno.store.core.exception.BusinessException;
import com.planno.store.core.exception.NotFoundException;
import com.planno.store.core.security.AuthenticatedUser;
import com.planno.store.payment.account.domain.PaymentProviderAccount;
import com.planno.store.payment.account.dto.PublicTransparentCheckoutCapabilitiesResponse;
import com.planno.store.payment.account.dto.PaymentProviderAccountStatusResponse;
import com.planno.store.payment.account.repository.PaymentProviderAccountRepository;
import com.planno.store.payment.domain.PaymentProvider;
import com.planno.store.store.service.CurrentStoreProvider;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentProviderAccountService {

    private final PaymentProviderAccountRepository accountRepository;
    private final CurrentStoreProvider currentStoreProvider;
    private final CredentialCipherService credentialCipherService;

    public PaymentProviderAccountService(
            PaymentProviderAccountRepository accountRepository,
            CurrentStoreProvider currentStoreProvider,
            CredentialCipherService credentialCipherService
    ) {
        this.accountRepository = accountRepository;
        this.currentStoreProvider = currentStoreProvider;
        this.credentialCipherService = credentialCipherService;
    }

    @Transactional
    public PaymentProviderAccountStatusResponse getCurrentAccountStatus() {
        return PaymentProviderAccountStatusResponse.from(getOrCreateCurrentStoreAccount());
    }

    @Transactional
    public PublicTransparentCheckoutCapabilitiesResponse getPublicTransparentCheckoutCapabilities() {
        PaymentProviderAccount account = getOrCreateCurrentStoreAccount();
        boolean connected = account.hasValidAccessToken();
        boolean ready = connected && account.getPublicKey() != null && !account.getPublicKey().isBlank();
        return new PublicTransparentCheckoutCapabilitiesResponse(
                true,
                connected,
                ready,
                account.getProvider().name(),
                ready ? account.getPublicKey() : null
        );
    }

    @Transactional
    public PaymentProviderAccount connectCurrentStoreAccount(MercadoPagoOAuthTokenResponse tokenResponse) {
        return connectAccount(getOrCreateCurrentStoreAccount(), tokenResponse);
    }

    @Transactional
    public PaymentProviderAccount connectAccountForStore(UUID storeId, MercadoPagoOAuthTokenResponse tokenResponse) {
        PaymentProviderAccount account = accountRepository.findByStoreIdAndProvider(storeId, PaymentProvider.MERCADO_PAGO)
                .orElseThrow(() -> new NotFoundException("Payment provider account not found"));
        return connectAccount(account, tokenResponse);
    }

    @Transactional
    public PaymentProviderAccountStatusResponse disconnectCurrentStoreAccount() {
        PaymentProviderAccount account = getOrCreateCurrentStoreAccount();
        account.disconnect();
        return PaymentProviderAccountStatusResponse.from(account);
    }

    @Transactional(readOnly = true)
    public PaymentProviderAccount requireConnectedAccountForCurrentStore() {
        PaymentProviderAccount account = getOrCreateCurrentStoreAccount();
        if (!account.hasValidAccessToken()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "The current store does not have a connected Mercado Pago account");
        }
        return account;
    }

    @Transactional(readOnly = true)
    public PaymentProviderAccount requireTransparentReadyAccountForCurrentStore() {
        PaymentProviderAccount account = requireConnectedAccountForCurrentStore();
        if (account.getPublicKey() == null || account.getPublicKey().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "The current store is not ready for Mercado Pago transparent checkout");
        }
        return account;
    }

    @Transactional(readOnly = true)
    public PaymentProviderAccount requireConnectedAccountForStore(UUID storeId) {
        PaymentProviderAccount account = accountRepository.findByStoreIdAndProvider(storeId, PaymentProvider.MERCADO_PAGO)
                .orElseThrow(() -> new NotFoundException("Payment provider account not found"));
        if (!account.hasValidAccessToken()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "The current store does not have a connected Mercado Pago account");
        }
        return account;
    }

    @Transactional(readOnly = true)
    public PaymentProviderAccount getByProviderUserId(PaymentProvider provider, String providerUserId) {
        return accountRepository.findByProviderAndProviderUserId(provider, providerUserId)
                .orElseThrow(() -> new NotFoundException("Payment provider account not found"));
    }

    @Transactional(readOnly = true)
    public String decryptAccessToken(PaymentProviderAccount account) {
        if (!account.hasValidAccessToken()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "The payment provider account does not have a valid access token");
        }
        return credentialCipherService.decrypt(account.getAccessTokenEncrypted());
    }

    @Transactional
    public PaymentProviderAccount getOrCreateCurrentStoreAccount() {
        UUID storeId = currentStoreProvider.getCurrentStoreId();
        return accountRepository.findByStoreIdAndProvider(storeId, PaymentProvider.MERCADO_PAGO)
                .orElseGet(() -> accountRepository.save(PaymentProviderAccount.create(currentStoreProvider.getCurrentStore(), PaymentProvider.MERCADO_PAGO)));
    }

    private PaymentProviderAccount connectAccount(PaymentProviderAccount account, MercadoPagoOAuthTokenResponse tokenResponse) {
        account.connect(
                tokenResponse.userId(),
                credentialCipherService.encrypt(tokenResponse.accessToken()),
                credentialCipherService.encrypt(tokenResponse.refreshToken()),
                tokenResponse.expiresAt(),
                tokenResponse.publicKey(),
                tokenResponse.scope()
        );
        return account;
    }

    @Transactional(readOnly = true)
    public boolean currentUserCanManageCurrentStore() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof AuthenticatedUser authenticatedUser) {
            return authenticatedUser.canAccessStore(currentStoreProvider.getCurrentStoreId());
        }
        return false;
    }
}
