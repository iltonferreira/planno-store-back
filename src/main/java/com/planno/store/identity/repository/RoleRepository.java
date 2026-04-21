package com.planno.store.identity.repository;

import com.planno.store.identity.domain.Role;
import com.planno.store.identity.domain.RoleName;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByName(RoleName name);
}

