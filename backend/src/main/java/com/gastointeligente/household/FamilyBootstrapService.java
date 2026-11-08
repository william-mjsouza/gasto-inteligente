package com.gastointeligente.household;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import com.gastointeligente.auth.AppUser;
import com.gastointeligente.auth.AppUserRepository;
import com.gastointeligente.auth.PasswordPolicy;
import com.gastointeligente.auth.PasswordService;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gastointeligente.shared.audit.AuditService;

/**
 * Criação controlada de família com primeiro administrador e recuperação administrativa de
 * acesso. Não há endpoint HTTP para isto: é acionado apenas pelo {@link AdminCommandRunner}, com
 * acesso ao servidor ou ao banco. Não existem credenciais padrão no código.
 */
@Service
public class FamilyBootstrapService {

	private final FamilyRepository families;

	private final FamilySettingsRepository settings;

	private final AppUserRepository users;

	private final MembershipRepository memberships;

	private final PasswordEncoder encoder;

	private final PasswordService passwords;

	private final AuditService audit;

	private final Clock clock;

	public FamilyBootstrapService(FamilyRepository families, FamilySettingsRepository settings,
			AppUserRepository users, MembershipRepository memberships, PasswordEncoder encoder,
			PasswordService passwords, AuditService audit, Clock clock) {
		this.families = families;
		this.settings = settings;
		this.users = users;
		this.memberships = memberships;
		this.encoder = encoder;
		this.passwords = passwords;
		this.audit = audit;
		this.clock = clock;
	}

	public record CreatedFamily(Family family, AppUser admin, Membership membership) {
	}

	@Transactional
	public CreatedFamily createFamilyWithAdmin(String familyName, String adminEmail, String adminDisplayName,
			String adminPassword) {
		require(familyName, "nome da família");
		require(adminDisplayName, "nome do administrador");
		String email = AppUser.normalizeEmail(adminEmail);
		if (email.isEmpty() || !email.contains("@")) {
			throw new IllegalArgumentException("E-mail do administrador inválido.");
		}
		String violation = PasswordPolicy.violation(adminPassword);
		if (violation != null) {
			throw new IllegalArgumentException(violation);
		}
		if (this.users.existsByEmail(email)) {
			throw new IllegalStateException("Já existe usuário com este e-mail.");
		}
		Instant now = this.clock.instant();
		Family family = this.families.save(new Family(familyName, now));
		this.settings.save(FamilySettings.defaults(family.getId(), now));
		AppUser admin = this.users.save(new AppUser(email, adminDisplayName, this.encoder.encode(adminPassword), now));
		Membership membership = this.memberships.save(new Membership(family.getId(), admin, MembershipRole.ADMIN, now));
		this.memberships.flush();
		this.audit.record(family.getId(), null, "FAMILY_CREATED", "FAMILY", family.getId(),
				Map.of("source", "admin-command"));
		return new CreatedFamily(family, admin, membership);
	}

	/**
	 * Recuperação de acesso: redefine a senha, reativa o usuário e a associação e garante o papel
	 * ADMIN. Revoga todas as sessões existentes desse usuário.
	 */
	@Transactional
	public void recoverAdminAccess(String email, String newPassword) {
		Membership membership = this.memberships.findByUserEmail(AppUser.normalizeEmail(email))
			.orElseThrow(() -> new IllegalStateException("Usuário não encontrado."));
		Instant now = this.clock.instant();
		AppUser user = membership.getUser();
		String violation = PasswordPolicy.violation(newPassword);
		if (violation != null) {
			throw new IllegalArgumentException(violation);
		}
		this.passwords.resetPassword(user, newPassword, membership.getFamilyId(), null);
		user.activate(now);
		membership.changeStatus(MembershipStatus.ACTIVE, now);
		membership.changeRole(MembershipRole.ADMIN, now);
		this.audit.record(membership.getFamilyId(), null, "ADMIN_ACCESS_RECOVERED", "MEMBERSHIP",
				membership.getId(), Map.of("source", "admin-command"));
	}

	private static void require(String value, String label) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Informe o " + label + ".");
		}
	}

}
