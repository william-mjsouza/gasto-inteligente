package com.gastointeligente.auth;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.gastointeligente.shared.web.ApiErrorCode;
import com.gastointeligente.shared.web.ApiErrorWriter;

import org.springframework.web.filter.OncePerRequestFilter;

/** Bloqueia o processamento do login quando o limite de falhas foi atingido (HTTP 429). */
public class LoginThrottleFilter extends OncePerRequestFilter {

	private final String loginPath;

	private final LoginAttemptService attempts;

	private final ApiErrorWriter errorWriter;

	public LoginThrottleFilter(String loginPath, LoginAttemptService attempts, ApiErrorWriter errorWriter) {
		this.loginPath = loginPath;
		this.attempts = attempts;
		this.errorWriter = errorWriter;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !("POST".equals(request.getMethod()) && this.loginPath.equals(request.getServletPath()));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (this.attempts.isBlocked(request.getParameter(AuthJsonHandlers.EMAIL_PARAMETER),
				request.getRemoteAddr())) {
			this.errorWriter.write(request, response, ApiErrorCode.TOO_MANY_ATTEMPTS);
			return;
		}
		chain.doFilter(request, response);
	}

}
