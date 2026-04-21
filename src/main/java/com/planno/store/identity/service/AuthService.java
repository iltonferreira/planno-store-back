package com.planno.store.identity.service;

import com.planno.store.core.security.AuthenticatedUser;
import com.planno.store.core.security.JwtProperties;
import com.planno.store.core.security.JwtService;
import com.planno.store.identity.dto.AuthResponse;
import com.planno.store.identity.dto.LoginRequest;
import com.planno.store.identity.dto.MeResponse;
import com.planno.store.identity.repository.UserRepository;
import java.util.Comparator;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final UserRepository userRepository;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            JwtProperties jwtProperties,
            UserRepository userRepository
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.userRepository = userRepository;
    }

    public AuthResponse login(LoginRequest request) {
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        String token = jwtService.generateToken(user);

        return new AuthResponse(
                token,
                "Bearer",
                jwtProperties.expirationMinutes(),
                user.getUserId(),
                user.getUsername(),
                user.getAuthorities().stream().map(Object::toString).sorted().toList(),
                user.getStoreIds().stream().sorted(Comparator.comparing(Object::toString)).toList()
        );
    }

    @Transactional(readOnly = true)
    public MeResponse me() {
        AuthenticatedUser principal = (AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        var user = userRepository.findByEmailIgnoreCase(principal.getUsername())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                principal.getAuthorities().stream().map(Object::toString).sorted().toList(),
                principal.getStoreIds().stream().sorted(Comparator.comparing(Object::toString)).toList()
        );
    }
}

