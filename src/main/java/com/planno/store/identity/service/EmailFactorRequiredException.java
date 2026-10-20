package com.planno.store.identity.service;

public class EmailFactorRequiredException extends RuntimeException {
    public EmailFactorRequiredException() {
        super("Um código de acesso foi enviado para seu e-mail.");
    }
}
