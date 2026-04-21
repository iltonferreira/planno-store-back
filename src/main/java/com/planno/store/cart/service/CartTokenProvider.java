package com.planno.store.cart.service;

import com.planno.store.core.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class CartTokenProvider {

    private final CartTokenProperties properties;

    public CartTokenProvider(CartTokenProperties properties) {
        this.properties = properties;
    }

    public String requireCurrentToken() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Cart token is unavailable outside an HTTP request");
        }

        HttpServletRequest request = attributes.getRequest();
        String token = request.getHeader(properties.tokenHeaderName());
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Cart token header is required");
        }
        return token.trim();
    }
}

