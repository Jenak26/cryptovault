package com.cryptovault.repository;

import com.cryptovault.entity.Role;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for {@link Role}. Used to look up the default {@code USER} role at registration.
 */
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);
}
