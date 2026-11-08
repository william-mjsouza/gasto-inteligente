package com.gastointeligente.auth;

import com.gastointeligente.household.Family;
import com.gastointeligente.household.FamilyRepository;
import com.gastointeligente.shared.web.ApiErrorCode;
import com.gastointeligente.shared.web.ApiException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeService {

	private final FamilyRepository families;

	public MeService(FamilyRepository families) {
		this.families = families;
	}

	@Transactional(readOnly = true)
	public AuthDtos.MeResponse describe(AppUserPrincipal principal) {
		Family family = this.families.findById(principal.familyId())
			.orElseThrow(() -> new ApiException(ApiErrorCode.UNAUTHENTICATED));
		return new AuthDtos.MeResponse(principal.userId(), principal.email(), principal.displayName(),
				principal.role(), new AuthDtos.FamilySummary(family.getId(), family.getName()));
	}

}
