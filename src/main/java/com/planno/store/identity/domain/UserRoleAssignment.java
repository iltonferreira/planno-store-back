package com.planno.store.identity.domain;

import com.planno.store.core.auditing.AuditableEntity;
import com.planno.store.store.domain.Store;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_role_assignments")
public class UserRoleAssignment extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    protected UserRoleAssignment() {
    }

    public UserRoleAssignment(User user, Role role, Store store) {
        this.user = user;
        this.role = role;
        this.store = store;
    }

    public User getUser() {
        return user;
    }

    public Role getRole() {
        return role;
    }

    public Store getStore() {
        return store;
    }
}

