package com.planno.store.identity.repository;

import com.planno.store.identity.domain.UserRoleAssignment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, UUID> {

    @EntityGraph(attributePaths = {"role", "store"})
    List<UserRoleAssignment> findAllByUserId(UUID userId);

    boolean existsByUserIdAndRoleIdAndStoreId(UUID userId, UUID roleId, UUID storeId);
}

