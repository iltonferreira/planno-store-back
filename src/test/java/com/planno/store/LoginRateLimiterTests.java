package com.planno.store;
import org.junit.jupiter.api.Test;
import com.planno.store.identity.service.LoginRateLimiter;
import com.planno.store.core.exception.BusinessException;
import static org.junit.jupiter.api.Assertions.*;
class LoginRateLimiterTests {
    @Test void changingIpDoesNotBypassAccountLimit() {
        var limiter = new LoginRateLimiter();
        for (int i = 0; i < 10; i++) limiter.check("ip-" + i, "User@Example.com");
        var error = assertThrows(BusinessException.class, () -> limiter.check("other-ip", " user@example.com "));
        assertEquals(429, error.getStatus().value());
        assertDoesNotThrow(() -> limiter.check("other-ip", "other@example.com"));
    }
    @Test void rotatingAccountsDoesNotBypassIpLimit() {
        var limiter = new LoginRateLimiter();
        for (int i = 0; i < 30; i++) limiter.check("same-ip", "user" + i + "@example.com");
        assertThrows(BusinessException.class, () -> limiter.check("same-ip", "new@example.com"));
    }
}
