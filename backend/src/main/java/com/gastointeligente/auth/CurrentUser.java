package com.gastointeligente.auth;

import com.gastointeligente.shared.web.ApiErrorCode;
import com.gastointeligente.shared.web.ApiException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Acesso ao usuário autenticado da requisição atual. */
public final class CurrentUser {

	private CurrentUser() {
	}

	public static AppUserPrincipal get() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
			return principal;
		}
		throw new ApiException(ApiErrorCode.UNAUTHENTICATED);
	}

}
