package com.gastointeligente.support;

import java.util.UUID;

import com.gastointeligente.auth.AppUser;
import com.gastointeligente.auth.AppUserRepository;
import com.gastointeligente.household.FamilyBootstrapService;
import com.gastointeligente.household.Membership;
import com.gastointeligente.household.MembershipRepository;
import com.gastointeligente.household.MembershipRole;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cria famílias e membros sintéticos com e-mails únicos por teste. Usa os mesmos serviços do
 * produto (bootstrap administrativo), sem atalhos no banco.
 */
@Component
public class TestFamilies {

	public static final String PASSWORD = "senha-de-teste-123";

	private final FamilyBootstrapService bootstrap;

	private final AppUserRepository users;

	private final MembershipRepository memberships;

	private final PasswordEncoder encoder;

	private final TransactionTemplate tx;

	public TestFamilies(FamilyBootstrapService bootstrap, AppUserRepository users, MembershipRepository memberships,
			PasswordEncoder encoder, TransactionTemplate tx) {
		this.bootstrap = bootstrap;
		this.users = users;
		this.memberships = memberships;
		this.encoder = encoder;
		this.tx = tx;
	}

	public record TestUser(String email, UUID userId, UUID membershipId) {
	}

	public record TestFamily(UUID familyId, TestUser admin) {
	}

	public static String uniqueEmail(String prefix) {
		return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@exemplo.test";
	}

	public TestFamily createFamily(String name) {
		String email = uniqueEmail("admin");
		var created = this.bootstrap.createFamilyWithAdmin(name, email, "Admin " + name, PASSWORD);
		return new TestFamily(created.family().getId(),
				new TestUser(email, created.admin().getId(), created.membership().getId()));
	}

	public TestUser addMember(TestFamily family, MembershipRole role) {
		String email = uniqueEmail(role.name().toLowerCase(java.util.Locale.ROOT));
		return this.tx.execute((status) -> {
			AppUser user = this.users.save(new AppUser(email, "Pessoa " + role, this.encoder.encode(PASSWORD),
					java.time.Instant.now()));
			Membership membership = this.memberships
				.save(new Membership(family.familyId(), user, role, java.time.Instant.now()));
			return new TestUser(email, user.getId(), membership.getId());
		});
	}

}
