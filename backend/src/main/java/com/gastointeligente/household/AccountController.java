package com.gastointeligente.household;

import java.util.UUID;

import com.gastointeligente.auth.CurrentUser;
import com.gastointeligente.shared.web.PageResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

	private final AccountService service;

	public AccountController(AccountService service) {
		this.service = service;
	}

	@GetMapping
	PageResponse<HouseholdDtos.AccountResponse> list(@RequestParam(required = false) AccountKind kind,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
			@RequestParam(required = false) String sort) {
		return this.service.list(CurrentUser.get(), kind, page, size, sort);
	}

	@GetMapping("/{id}")
	HouseholdDtos.AccountResponse get(@PathVariable UUID id) {
		return this.service.get(CurrentUser.get(), id);
	}

	@PostMapping
	ResponseEntity<HouseholdDtos.AccountResponse> create(@Valid @RequestBody HouseholdDtos.AccountCreateRequest body) {
		return ResponseEntity.status(HttpStatus.CREATED).body(this.service.create(CurrentUser.get(), body));
	}

	@PatchMapping("/{id}")
	HouseholdDtos.AccountResponse update(@PathVariable UUID id,
			@Valid @RequestBody HouseholdDtos.AccountPatchRequest body) {
		return this.service.update(CurrentUser.get(), id, body);
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable UUID id, @RequestParam(required = false) Long version) {
		this.service.delete(CurrentUser.get(), id, version);
		return ResponseEntity.noContent().build();
	}

}
