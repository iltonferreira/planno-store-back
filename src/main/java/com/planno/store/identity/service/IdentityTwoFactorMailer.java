package com.planno.store.identity.service;

import com.planno.store.identity.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Component
public class IdentityTwoFactorMailer {
    private final JavaMailSender mailSender;
    private final String mailHost;
    private final String from;

    public IdentityTwoFactorMailer(JavaMailSender mailSender,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${app.security.email-two-factor.from:}") String from) {
        this.mailSender = mailSender;
        this.mailHost = mailHost == null ? "" : mailHost.trim();
        this.from = from == null ? "" : from.trim();
    }

    public void send(User user, String code) {
        if (mailHost.isBlank() || from.isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "O envio do código de acesso não está disponível.");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(user.getEmail());
        message.setSubject("Seu código de acesso à Planno Store");
        message.setText("Olá " + user.getFirstName() + ",\n\nSeu código de acesso é " + code
                + ". Ele expira em 10 minutos e pode ser usado uma única vez.\n\n"
                + "Se você não tentou entrar na sua loja, ignore esta mensagem.");
        try {
            mailSender.send(message);
        } catch (RuntimeException failure) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Não foi possível enviar o código de acesso. Tente novamente mais tarde.");
        }
    }
}
