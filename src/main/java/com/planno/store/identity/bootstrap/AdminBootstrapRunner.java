package com.planno.store.identity.bootstrap;

import com.planno.store.identity.domain.Role;
import com.planno.store.identity.domain.RoleName;
import com.planno.store.identity.domain.User;
import com.planno.store.identity.domain.UserRoleAssignment;
import com.planno.store.identity.repository.RoleRepository;
import com.planno.store.identity.repository.UserRepository;
import com.planno.store.identity.repository.UserRoleAssignmentRepository;
import com.planno.store.store.domain.Store;
import com.planno.store.store.repository.StoreRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(20)
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AdminBootstrapProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleAssignmentRepository assignmentRepository;
    private final StoreRepository storeRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapRunner(
            AdminBootstrapProperties properties,
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleAssignmentRepository assignmentRepository,
            StoreRepository storeRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.assignmentRepository = assignmentRepository;
        this.storeRepository = storeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }

        ensureRoles();

        if (properties.email() == null || properties.email().isBlank()
                || properties.password() == null || properties.password().isBlank()) {
            log.warn("Admin bootstrap skipped because APP_BOOTSTRAP_ADMIN_EMAIL or APP_BOOTSTRAP_ADMIN_PASSWORD is missing.");
            return;
        }

        Optional<Store> activeStore = storeRepository.findFirstByStatusOrderByCreatedAtAsc(Store.StoreStatus.ACTIVE);
        if (activeStore.isEmpty()) {
            log.warn("Admin bootstrap skipped because no active store exists.");
            return;
        }

        User user = userRepository.findByEmailIgnoreCase(properties.email())
                .orElseGet(() -> userRepository.save(User.create(
                        properties.email(),
                        passwordEncoder.encode(properties.password()),
                        properties.firstName(),
                        properties.lastName()
                )));

        Role adminRole = roleRepository.findByName(RoleName.ADMIN)
                .orElseThrow(() -> new IllegalStateException("ADMIN role not found"));

        boolean assignmentExists = assignmentRepository.existsByUserIdAndRoleIdAndStoreId(
                user.getId(),
                adminRole.getId(),
                activeStore.get().getId()
        );
        if (!assignmentExists) {
            assignmentRepository.save(new UserRoleAssignment(user, adminRole, activeStore.get()));
            log.info("Admin bootstrap linked '{}' to the active store with ADMIN role.", user.getEmail());
        } else {
            log.info("Admin bootstrap skipped role assignment because it already exists for '{}'.", user.getEmail());
        }
    }

    private void ensureRoles() {
        for (RoleName roleName : RoleName.values()) {
            roleRepository.findByName(roleName).orElseGet(() -> {
                log.info("Creating missing role '{}'.", roleName);
                return roleRepository.save(new Role(roleName));
            });
        }
    }
}
