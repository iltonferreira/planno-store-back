package com.planno.store.core.security;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class AuthenticatedUser implements UserDetails {

    private final UUID userId;
    private final String email;
    private final String password;
    private final boolean active;
    private final Set<UUID> storeIds;
    private final Collection<? extends GrantedAuthority> authorities;

    public AuthenticatedUser(
            UUID userId,
            String email,
            String password,
            boolean active,
            Set<UUID> storeIds,
            Collection<? extends GrantedAuthority> authorities
    ) {
        this.userId = userId;
        this.email = email;
        this.password = password;
        this.active = active;
        this.storeIds = storeIds;
        this.authorities = authorities;
    }

    public UUID getUserId() {
        return userId;
    }

    public Set<UUID> getStoreIds() {
        return storeIds;
    }

    public boolean hasRole(String roleName) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_" + roleName));
    }

    public boolean canAccessStore(UUID storeId) {
        return storeId != null && (storeIds.contains(storeId) || hasRole("PLATFORM_ADMIN"));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return active;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return active;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
