package com.gastointeligente.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

	/** {@code email} deve estar normalizado com {@link AppUser#normalizeEmail(String)}. */
	Optional<AppUser> findByEmail(String email);

	boolean existsByEmail(String email);

}
