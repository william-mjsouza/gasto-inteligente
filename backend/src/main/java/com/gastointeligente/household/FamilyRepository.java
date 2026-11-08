package com.gastointeligente.household;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface FamilyRepository extends JpaRepository<Family, UUID> {

	/** Serializa alterações de acesso da família (por exemplo, a regra do último administrador). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select f from Family f where f.id = :id")
	Optional<Family> lockById(UUID id);

}
