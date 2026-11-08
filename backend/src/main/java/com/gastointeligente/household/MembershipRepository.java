package com.gastointeligente.household;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

	@EntityGraph(attributePaths = "user")
	Optional<Membership> findByIdAndFamilyId(UUID id, UUID familyId);

	@EntityGraph(attributePaths = "user")
	Page<Membership> findByFamilyId(UUID familyId, Pageable pageable);

	@EntityGraph(attributePaths = "user")
	Optional<Membership> findByUserEmail(String email);

	@Query("""
			select count(m) from Membership m
			where m.familyId = :familyId and m.role = com.gastointeligente.household.MembershipRole.ADMIN
			  and m.status = com.gastointeligente.household.MembershipStatus.ACTIVE
			  and m.user.status = com.gastointeligente.auth.UserStatus.ACTIVE
			""")
	long countActiveAdmins(UUID familyId);

	/** Estado de segurança atual de uma sessão, consultado a cada requisição autenticada. */
	@Query("""
			select new com.gastointeligente.household.MembershipSecuritySnapshot(
			    m.role, m.status, u.status, u.securityStamp, u.displayName)
			from Membership m join m.user u
			where m.id = :membershipId
			""")
	Optional<MembershipSecuritySnapshot> findSecuritySnapshot(UUID membershipId);

}
