package com.planno.store.identity.service;

import com.planno.store.core.security.AuthenticatedUser;
import com.planno.store.identity.domain.UserRoleAssignment;
import com.planno.store.identity.domain.UserStatus;
import com.planno.store.identity.repository.UserRepository;
import com.planno.store.identity.repository.UserRoleAssignmentRepository;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class ApplicationUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserRoleAssignmentRepository assignmentRepository;

    public ApplicationUserDetailsService(
            UserRepository userRepository,
            UserRoleAssignmentRepository assignmentRepository
    ) {
        this.userRepository = userRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var user = userRepository.findByEmailIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        var assignments = assignmentRepository.findAllByUserId(user.getId());
        var authorities = assignments.stream()
                .map(assignment -> new SimpleGrantedAuthority("ROLE_" + assignment.getRole().getName().name()))
                .collect(java.util.stream.Collectors.toSet());

        Set<UUID> storeIds = assignments.stream()
                .map(UserRoleAssignment::getStore)
                .filter(java.util.Objects::nonNull)
                .map(store -> store.getId())
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        return new AuthenticatedUser(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getStatus() == UserStatus.ACTIVE,
                user.mustChangePassword(),
                storeIds,
                authorities
        );
    }
}
