package com.gastointeligente.household;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Toda consulta é restrita à família; não há busca de conta apenas por ID. */
public interface AccountRepository extends JpaRepository<Account, UUID> {

	Optional<Account> findByIdAndFamilyId(UUID id, UUID familyId);

	Page<Account> findByFamilyId(UUID familyId, Pageable pageable);

	Page<Account> findByFamilyIdAndKind(UUID familyId, AccountKind kind, Pageable pageable);

	@Query("select count(a) > 0 from Account a where a.familyId = :familyId and lower(a.name) = lower(:name) and a.id <> :exceptId")
	boolean existsNameInFamily(UUID familyId, String name, UUID exceptId);

}
