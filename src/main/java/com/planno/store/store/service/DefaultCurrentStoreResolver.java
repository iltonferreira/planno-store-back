package com.planno.store.store.service;

import com.planno.store.core.exception.NotFoundException;
import com.planno.store.core.security.AuthenticatedUser;
import com.planno.store.core.storecontext.StoreContext;
import com.planno.store.core.storecontext.StoreContextProperties;
import com.planno.store.core.storecontext.StoreContextSource;
import com.planno.store.store.domain.Store;
import com.planno.store.store.repository.StoreRepository;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class DefaultCurrentStoreResolver implements CurrentStoreResolver {

    private final StoreRepository storeRepository;
    private final StoreContextProperties properties;

    public DefaultCurrentStoreResolver(StoreRepository storeRepository, StoreContextProperties properties) {
        this.storeRepository = storeRepository;
        this.properties = properties;
    }

    @Override
    public StoreContext resolve() {
        String headerSlug = resolveRequestedStoreSlug();
        AuthenticatedUser authenticatedUser = resolveAuthenticatedUser();

        if (StringUtils.hasText(headerSlug)) {
            Store store = storeRepository.findBySlugIgnoreCaseAndStatus(headerSlug, Store.StoreStatus.ACTIVE)
                    .orElseThrow(() -> new NotFoundException("Store not found for the provided context"));
            validateAccess(authenticatedUser, store.getId());
            return new StoreContext(store.getId(), store.getSlug(), StoreContextSource.REQUEST_HEADER);
        }

        if (authenticatedUser != null && authenticatedUser.getStoreIds().size() == 1) {
            UUID storeId = authenticatedUser.getStoreIds().iterator().next();
            Store store = storeRepository.findById(storeId)
                    .orElseThrow(() -> new NotFoundException("Authenticated store not found"));
            return new StoreContext(store.getId(), store.getSlug(), StoreContextSource.AUTHENTICATED_USER);
        }

        Store store = storeRepository.findFirstByStatusOrderByCreatedAtAsc(Store.StoreStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("No active store configured"));
        validateAccess(authenticatedUser, store.getId());
        return new StoreContext(store.getId(), store.getSlug(), StoreContextSource.DEFAULT_ACTIVE_STORE);
    }

    private String resolveRequestedStoreSlug() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return null;
        }
        String headerValue = servletRequestAttributes.getRequest().getHeader(properties.headerName());
        return StringUtils.hasText(headerValue) ? headerValue.trim() : null;
    }

    private AuthenticatedUser resolveAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUser authenticatedUser) {
            return authenticatedUser;
        }
        return null;
    }

    private void validateAccess(AuthenticatedUser authenticatedUser, UUID storeId) {
        if (authenticatedUser == null || authenticatedUser.getStoreIds().isEmpty()) {
            return;
        }
        if (!authenticatedUser.canAccessStore(storeId)) {
            throw new AccessDeniedException("You do not have access to the requested store");
        }
    }
}

