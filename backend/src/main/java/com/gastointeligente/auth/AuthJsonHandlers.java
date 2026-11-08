package com.gastointeligente.auth;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.gastointeligente.shared.audit.AuditService;
import com.gastointeligente.shared.web.ApiErrorCode;
import com.gastointeligente.shared.web.ApiErrorWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;

/**
 * Handlers JSON do login por formulário do Spring Security e do logout. A falha é sempre genérica:
 * não informa se o e-mail existe, se a senha está errada ou se o acesso foi desativado.
 */
@Component
public class AuthJsonHandlers implements AuthenticationSuccessHandler, AuthenticationFailureHandler,
		LogoutSuccessHandler {

	public static final String EMAIL_PARAMETER = "email";

	public static final String PASSWORD_PARAMETER = "password";

	private static final Logger log = LoggerFactory.getLogger(AuthJsonHandlers.class);

	private final LoginAttemptService attempts;

	private final MeService meService;

	private final AuditService audit;

	private final ApiErrorWriter errorWriter;

	private final JsonMapper jsonMapper;

	public AuthJsonHandlers(LoginAttemptService attempts, MeService meService, AuditService audit,
			ApiErrorWriter errorWriter, JsonMapper jsonMapper) {
		this.attempts = attempts;
		this.meService = meService;
		this.audit = audit;
		this.errorWriter = errorWriter;
		this.jsonMapper = jsonMapper;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException {
		AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
		this.attempts.recordSuccess(request.getParameter(EMAIL_PARAMETER));
		this.audit.recordStandalone(principal.familyId(), principal.userId(), "LOGIN_SUCCEEDED", "USER",
				principal.userId());
		// Após a autenticação, o CsrfAuthenticationStrategy já descartou o token anterior e expôs
		// um novo nesta requisição: o cliente deve passar a usá-lo.
		CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
		AuthDtos.LoginResponse body = new AuthDtos.LoginResponse(this.meService.describe(principal),
				(csrf != null) ? AuthDtos.CsrfResponse.of(csrf) : null);
		writeJson(response, HttpStatus.OK, body);
	}

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException {
		this.attempts.recordFailure(request.getParameter(EMAIL_PARAMETER), request.getRemoteAddr());
		// Não registra o e-mail informado nem o motivo detalhado.
		log.info("Falha de login ({})", exception.getClass().getSimpleName());
		this.errorWriter.write(request, response, ApiErrorCode.INVALID_CREDENTIALS);
	}

	@Override
	public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) {
		if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
			this.audit.recordStandalone(principal.familyId(), principal.userId(), "LOGOUT", "USER",
					principal.userId());
		}
		response.setStatus(HttpStatus.NO_CONTENT.value());
	}

	private void writeJson(HttpServletResponse response, HttpStatus status, Object body) throws IOException {
		response.setStatus(status.value());
		response.setCharacterEncoding("UTF-8");
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		this.jsonMapper.writeValue(response.getOutputStream(), body);
	}

}
