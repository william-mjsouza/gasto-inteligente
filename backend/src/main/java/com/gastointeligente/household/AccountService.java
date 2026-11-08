package com.gastointeligente.household;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.gastointeligente.auth.AppUserPrincipal;
import com.gastointeligente.shared.audit.AuditService;
import com.gastointeligente.shared.web.ApiErrorCode;
import com.gastointeligente.shared.web.ApiException;
import com.gastointeligente.shared.web.PageResponse;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Contas de caixa e cartões. Toda leitura e escrita é restrita à família do usuário autenticado. */
@Service
public class AccountService {

	static final Set<String> SORTABLE = Set.of("name", "createdAt", "kind", "status");

	private static final UUID NO_ID = new UUID(0, 0);

	private final AccountRepository accounts;

	private final AuditService audit;

	private final Clock clock;

	public AccountService(AccountRepository accounts, AuditService audit, Clock clock) {
		this.accounts = accounts;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PageResponse<HouseholdDtos.AccountResponse> list(AppUserPrincipal principal, AccountKind kind,
			Integer page, Integer size, String sort) {
		Pageable pageable = PageResponse.pageable(page, size, sort, SORTABLE, Sort.by("name").and(Sort.by("id")));
		var result = (kind == null) ? this.accounts.findByFamilyId(principal.familyId(), pageable)
				: this.accounts.findByFamilyIdAndKind(principal.familyId(), kind, pageable);
		return PageResponse.of(result, HouseholdDtos.AccountResponse::of);
	}

	@Transactional(readOnly = true)
	public HouseholdDtos.AccountResponse get(AppUserPrincipal principal, UUID id) {
		return HouseholdDtos.AccountResponse.of(load(principal, id));
	}

	@Transactional
	public HouseholdDtos.AccountResponse create(AppUserPrincipal principal, HouseholdDtos.AccountCreateRequest req) {
		String name = normalizeName(req.name());
		ensureUniqueName(principal.familyId(), name, NO_ID);
		Account account = this.accounts.save(new Account(principal.familyId(), name, req.kind(),
				this.clock.instant()));
		this.accounts.flush();
		this.audit.record(principal.familyId(), principal.userId(), "ACCOUNT_CREATED", "ACCOUNT", account.getId(),
				Map.of("kind", account.getKind().name()));
		return HouseholdDtos.AccountResponse.of(account);
	}

	@Transactional
	public HouseholdDtos.AccountResponse update(AppUserPrincipal principal, UUID id,
			HouseholdDtos.AccountPatchRequest req) {
		Account account = load(principal, id);
		Versions.require(account.getVersion(), req.version());
		Map<String, Object> changes = new LinkedHashMap<>();
		if (req.name() != null) {
			String name = normalizeName(req.name());
			if (!name.equals(account.getName())) {
				ensureUniqueName(principal.familyId(), name, account.getId());
				changes.put("name", "changed");
				account.rename(name, this.clock.instant());
			}
		}
		if (req.status() != null && req.status() != account.getStatus()) {
			changes.put("status", Map.of("from", account.getStatus().name(), "to", req.status().name()));
			account.changeStatus(req.status(), this.clock.instant());
		}
		this.accounts.flush();
		if (!changes.isEmpty()) {
			this.audit.record(principal.familyId(), principal.userId(), "ACCOUNT_UPDATED", "ACCOUNT",
					account.getId(), changes);
		}
		return HouseholdDtos.AccountResponse.of(account);
	}

	/**
	 * Exclui a conta. Nesta etapa ainda não existem lançamentos; quando existirem, contas com
	 * vínculos devem ser arquivadas (as chaves estrangeiras impedirão a exclusão).
	 */
	@Transactional
	public void delete(AppUserPrincipal principal, UUID id, Long version) {
		Account account = load(principal, id);
		if (version == null) {
			throw ApiException.validation("version", "Obrigatório.");
		}
		Versions.require(account.getVersion(), version);
		this.accounts.delete(account);
		this.accounts.flush();
		this.audit.record(principal.familyId(), principal.userId(), "ACCOUNT_DELETED", "ACCOUNT", id, Map.of());
	}

	private Account load(AppUserPrincipal principal, UUID id) {
		// Conta de outra família é indistinguível de conta inexistente: 404.
		return this.accounts.findByIdAndFamilyId(id, principal.familyId()).orElseThrow(ApiException::notFound);
	}

	private void ensureUniqueName(UUID familyId, String name, UUID exceptId) {
		if (this.accounts.existsNameInFamily(familyId, name, exceptId)) {
			throw new ApiException(ApiErrorCode.DUPLICATE_NAME,
					java.util.List.of(new com.gastointeligente.shared.web.ApiError.Detail("name", null,
							"Já existe uma conta com este nome.")));
		}
	}

	private static String normalizeName(String name) {
		String value = (name == null) ? "" : name.strip();
		if (value.isEmpty()) {
			throw ApiException.validation("name", "Informe o nome.");
		}
		return value;
	}

}
