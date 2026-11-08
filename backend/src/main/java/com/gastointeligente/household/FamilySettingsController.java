package com.gastointeligente.household;

import com.gastointeligente.auth.CurrentUser;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Configurações da família do usuário autenticado. ADMIN e MEMBER podem consultar e alterar. */
@RestController
@RequestMapping("/api/v1/family/settings")
public class FamilySettingsController {

	private final FamilySettingsService service;

	public FamilySettingsController(FamilySettingsService service) {
		this.service = service;
	}

	@GetMapping
	HouseholdDtos.SettingsResponse get() {
		return this.service.get(CurrentUser.get());
	}

	@PatchMapping
	HouseholdDtos.SettingsResponse update(@Valid @RequestBody HouseholdDtos.SettingsPatchRequest body) {
		return this.service.update(CurrentUser.get(), body);
	}

}
