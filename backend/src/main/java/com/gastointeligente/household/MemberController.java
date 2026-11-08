package com.gastointeligente.household;

import java.util.UUID;

import com.gastointeligente.auth.CurrentUser;
import com.gastointeligente.shared.web.PageResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Administração de membros da família. Restrito a ADMIN (ver SecurityConfig). */
@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

	private final MemberService service;

	public MemberController(MemberService service) {
		this.service = service;
	}

	@GetMapping
	PageResponse<HouseholdDtos.MemberResponse> list(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size, @RequestParam(required = false) String sort) {
		return this.service.list(CurrentUser.get(), page, size, sort);
	}

	@GetMapping("/{id}")
	HouseholdDtos.MemberResponse get(@PathVariable UUID id) {
		return this.service.get(CurrentUser.get(), id);
	}

	@PostMapping
	ResponseEntity<HouseholdDtos.MemberResponse> create(@Valid @RequestBody HouseholdDtos.MemberCreateRequest body) {
		return ResponseEntity.status(HttpStatus.CREATED).body(this.service.create(CurrentUser.get(), body));
	}

	@PatchMapping("/{id}")
	HouseholdDtos.MemberResponse update(@PathVariable UUID id,
			@Valid @RequestBody HouseholdDtos.MemberPatchRequest body) {
		return this.service.update(CurrentUser.get(), id, body);
	}

	@PostMapping("/{id}/password")
	ResponseEntity<Void> resetPassword(@PathVariable UUID id,
			@Valid @RequestBody HouseholdDtos.PasswordResetRequest body) {
		this.service.resetPassword(CurrentUser.get(), id, body.newPassword());
		return ResponseEntity.noContent().build();
	}

}
