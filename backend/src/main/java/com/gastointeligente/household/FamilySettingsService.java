package com.gastointeligente.household;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

import com.gastointeligente.auth.AppUserPrincipal;
import com.gastointeligente.shared.audit.AuditService;
import com.gastointeligente.shared.web.ApiException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FamilySettingsService {

	private static final BigDecimal HUNDRED = new BigDecimal("100");

	private final FamilySettingsRepository repository;

	private final AuditService audit;

	private final Clock clock;

	public FamilySettingsService(FamilySettingsRepository repository, AuditService audit, Clock clock) {
		this.repository = repository;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public HouseholdDtos.SettingsResponse get(AppUserPrincipal principal) {
		return HouseholdDtos.SettingsResponse.of(load(principal));
	}

	@Transactional
	public HouseholdDtos.SettingsResponse update(AppUserPrincipal principal, HouseholdDtos.SettingsPatchRequest req) {
		FamilySettings settings = load(principal);
		Versions.require(settings.getVersion(), req.version());

		String timezone = null;
		if (req.timezone() != null) {
			try {
				timezone = ZoneId.of(req.timezone().strip()).getId();
			}
			catch (DateTimeException ex) {
				throw ApiException.validation("timezone", "Fuso horário inválido. Exemplo: America/Fortaleza.");
			}
		}
		BigDecimal margin = null;
		if (req.prudentMarginPercent() != null) {
			margin = new BigDecimal(req.prudentMarginPercent()).setScale(2);
			if (margin.compareTo(HUNDRED) > 0) {
				throw ApiException.validation("prudentMarginPercent", "Deve estar entre 0 e 100.");
			}
		}

		Map<String, Object> changes = new LinkedHashMap<>();
		putIfChanged(changes, "timezone", settings.getTimezone(), timezone);
		putIfChanged(changes, "averageWindowMonths", settings.getAverageWindowMonths(), req.averageWindowMonths());
		putIfChanged(changes, "prudentMarginPercent", settings.getPrudentMarginPercent().toPlainString(),
				(margin == null) ? null : margin.toPlainString());
		putIfChanged(changes, "cardPaymentOffsetMonths", settings.getCardPaymentOffsetMonths(),
				req.cardPaymentOffsetMonths());

		settings.update(timezone, req.averageWindowMonths(), margin, req.cardPaymentOffsetMonths(),
				this.clock.instant());
		this.repository.flush();
		this.audit.record(principal.familyId(), principal.userId(), "SETTINGS_UPDATED", "FAMILY_SETTINGS",
				principal.familyId(), changes);
		return HouseholdDtos.SettingsResponse.of(settings);
	}

	private FamilySettings load(AppUserPrincipal principal) {
		return this.repository.findById(principal.familyId()).orElseThrow(ApiException::notFound);
	}

	private static void putIfChanged(Map<String, Object> changes, String field, Object before, Object after) {
		if (after != null && !after.equals(before)) {
			changes.put(field, Map.of("from", before, "to", after));
		}
	}

}
