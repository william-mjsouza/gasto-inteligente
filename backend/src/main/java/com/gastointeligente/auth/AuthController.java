package com.gastointeligente.auth;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de sessão. Login ({@code POST /api/v1/auth/login}) e logout
 * ({@code POST /api/v1/auth/logout}) são processados pelos filtros do Spring Security.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final MeService meService;

	private final PasswordService passwordService;

	private final SecurityContextRepository contextRepository;

	public AuthController(MeService meService, PasswordService passwordService,
			SecurityContextRepository contextRepository) {
		this.meService = meService;
		this.passwordService = passwordService;
		this.contextRepository = contextRepository;
	}

	/**
	 * Token CSRF da sessão atual (anônima ou autenticada). O token muda após login e logout; o
	 * cliente deve buscá-lo novamente nesses momentos e enviá-lo no cabeçalho indicado.
	 */
	@GetMapping("/csrf")
	AuthDtos.CsrfResponse csrf(CsrfToken token) {
		return AuthDtos.CsrfResponse.of(token);
	}

	@GetMapping("/me")
	AuthDtos.MeResponse me() {
		return this.meService.describe(CurrentUser.get());
	}

	/** Troca a própria senha. Outras sessões do usuário são revogadas; a atual continua válida. */
	@PostMapping("/password")
	ResponseEntity<Void> changePassword(@Valid @RequestBody AuthDtos.ChangePasswordRequest body,
			HttpServletRequest request, HttpServletResponse response) {
		AppUserPrincipal principal = CurrentUser.get();
		UUID newStamp = this.passwordService.changeOwnPassword(principal, body.currentPassword(),
				body.newPassword(), request.getRemoteAddr());
		AppUserPrincipal updated = principal.withSecurityStamp(newStamp);
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(updated, null,
				updated.getAuthorities()));
		SecurityContextHolder.setContext(context);
		request.changeSessionId();
		this.contextRepository.saveContext(context, request, response);
		return ResponseEntity.noContent().build();
	}

}
