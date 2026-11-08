package com.gastointeligente.household;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.gastointeligente.auth.AppUser;
import com.gastointeligente.auth.AppUserPrincipal;
import com.gastointeligente.auth.AppUserRepository;
import com.gastointeligente.auth.PasswordPolicy;
import com.gastointeligente.auth.PasswordService;
import com.gastointeligente.shared.audit.AuditService;
import com.gastointeligente.shared.web.ApiErrorCode;
import com.gastointeligente.shared.web.ApiException;
import com.gastointeligente.shared.web.PageResponse;

import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administração de acessos da própria família. Somente ADMIN (verificado na configuração HTTP e
 * novamente aqui). Membros não são excluídos: são desativados, preservando a auditoria.
 */
@Service
public class MemberService {

	static final Set<String> SORTABLE = Set.of("createdAt", "role", "status");

	private final MembershipRepository memberships;

	private final AppUserRepository users;

	private final PasswordEncoder encoder;

	private final PasswordService passwords;

	private final AuditService audit;

	private final FamilyRepository families;

	private final Clock clock;

	public MemberService(MembershipRepository memberships, AppUserRepository users, PasswordEncoder encoder,
			PasswordService passwords, AuditService audit, FamilyRepository families, Clock clock) {
		this.memberships = memberships;
		this.families = families;
		this.users = users;
		this.encoder = encoder;
		this.passwords = passwords;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PageResponse<HouseholdDtos.MemberResponse> list(AppUserPrincipal admin, Integer page, Integer size,
			String sort) {
		requireAdmin(admin);
		var pageable = PageResponse.pageable(page, size, sort, SORTABLE, Sort.by("createdAt").and(Sort.by("id")));
		return PageResponse.of(this.memberships.findByFamilyId(admin.familyId(), pageable),
				HouseholdDtos.MemberResponse::of);
	}

	@Transactional(readOnly = true)
	public HouseholdDtos.MemberResponse get(AppUserPrincipal admin, UUID id) {
		requireAdmin(admin);
		return HouseholdDtos.MemberResponse.of(load(admin, id));
	}

	@Transactional
	public HouseholdDtos.MemberResponse create(AppUserPrincipal admin, HouseholdDtos.MemberCreateRequest req) {
		requireAdmin(admin);
		PasswordPolicy.validate("initialPassword", req.initialPassword());
		String email = AppUser.normalizeEmail(req.email());
		if (this.users.existsByEmail(email)) {
			// Mensagem genérica: não revela a qual família o e-mail pertence.
			throw new ApiException(ApiErrorCode.EMAIL_UNAVAILABLE);
		}
		Instant now = this.clock.instant();
		AppUser user = this.users.save(new AppUser(email, req.displayName(),
				this.encoder.encode(req.initialPassword()), now));
		Membership membership = this.memberships.save(new Membership(admin.familyId(), user, req.role(), now));
		this.memberships.flush();
		this.audit.record(admin.familyId(), admin.userId(), "MEMBER_CREATED", "MEMBERSHIP", membership.getId(),
				Map.of("role", req.role().name()));
		return HouseholdDtos.MemberResponse.of(membership);
	}

	@Transactional
	public HouseholdDtos.MemberResponse update(AppUserPrincipal admin, UUID id, HouseholdDtos.MemberPatchRequest req) {
		requireAdmin(admin);
		this.families.lockById(admin.familyId()).orElseThrow(ApiException::notFound);
		Membership membership = load(admin, id);
		Versions.require(membership.getVersion(), req.version());

		boolean wasActiveAdmin = membership.isActiveAdmin();
		Map<String, Object> changes = new LinkedHashMap<>();
		Instant now = this.clock.instant();
		if (req.role() != null && req.role() != membership.getRole()) {
			changes.put("role", Map.of("from", membership.getRole().name(), "to", req.role().name()));
			membership.changeRole(req.role(), now);
		}
		if (req.status() != null && req.status() != membership.getStatus()) {
			changes.put("status", Map.of("from", membership.getStatus().name(), "to", req.status().name()));
			membership.changeStatus(req.status(), now);
		}
		this.memberships.flush();
		if (wasActiveAdmin && !membership.isActiveAdmin() && this.memberships.countActiveAdmins(admin.familyId()) == 0) {
			// Lançar a exceção desfaz a transação inteira.
			throw new ApiException(ApiErrorCode.LAST_ADMIN);
		}
		if (!changes.isEmpty()) {
			this.audit.record(admin.familyId(), admin.userId(), "MEMBER_UPDATED", "MEMBERSHIP", membership.getId(),
					changes);
		}
		return HouseholdDtos.MemberResponse.of(membership);
	}

	/** Redefine a senha de um membro; todas as sessões dele são revogadas. */
	@Transactional
	public void resetPassword(AppUserPrincipal admin, UUID id, String newPassword) {
		requireAdmin(admin);
		Membership membership = load(admin, id);
		this.passwords.resetPassword(membership.getUser(), newPassword, admin.familyId(), admin.userId());
	}

	private Membership load(AppUserPrincipal admin, UUID id) {
		return this.memberships.findByIdAndFamilyId(id, admin.familyId()).orElseThrow(ApiException::notFound);
	}

	private static void requireAdmin(AppUserPrincipal principal) {
		if (!principal.isAdmin()) {
			throw new AccessDeniedException("ADMIN required");
		}
	}

}
