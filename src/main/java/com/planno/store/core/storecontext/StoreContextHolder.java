package com.planno.store.core.storecontext;

import java.util.Optional;

public final class StoreContextHolder {

    private static final ThreadLocal<StoreContext> CONTEXT = new ThreadLocal<>();

    private StoreContextHolder() {
    }

    public static void set(StoreContext context) {
        CONTEXT.set(context);
    }

    public static Optional<StoreContext> get() {
        return Optional.ofNullable(CONTEXT.get());
    }

    public static void clear() {
        CONTEXT.remove();
    }
}

