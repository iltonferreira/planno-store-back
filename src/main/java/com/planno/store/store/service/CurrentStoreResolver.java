package com.planno.store.store.service;

import com.planno.store.core.storecontext.StoreContext;

public interface CurrentStoreResolver {

    StoreContext resolve();
}

