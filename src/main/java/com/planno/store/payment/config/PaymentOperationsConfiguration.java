package com.planno.store.payment.config;

import com.planno.store.core.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Fail closed for new checkout/provider operations until homologation is approved. */
@Configuration(proxyBeanMethods = false)
public class PaymentOperationsConfiguration implements WebMvcConfigurer {
    private final boolean enabled;

    public PaymentOperationsConfiguration(@Value("${app.payment.operations-enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (enabled) return;
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if ("POST".equals(request.getMethod()) || "GET".equals(request.getMethod())
                        && (request.getRequestURI().endsWith("/connect") || request.getRequestURI().endsWith("/callback"))) {
                    throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Checkout indisponível: pagamentos aguardam homologação.");
                }
                return true;
            }
        }).addPathPatterns("/api/v1/public/orders", "/api/v1/public/payments",
                "/api/v1/public/payments/transparent", "/api/v1/admin/payments/*/refunds",
                "/api/v1/admin/payments/mercado-pago/connect",
                "/api/v1/public/payments/mercado-pago/oauth/callback");
    }
}
