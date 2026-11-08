package com.gastointeligente.auth;

import java.util.UUID;

import com.gastointeligente.household.MembershipRole;
import jakarta.validation.constraints.NotNull;

import org.springframework.security.web.csrf.CsrfToken;

/** DTOs de autenticação. */
public final class AuthDtos {

	private AuthDtos() {
	}

	public record CsrfResponse(String headerName, String token) {

		public static CsrfResponse of(CsrfToken token) {
			return new CsrfResponse(token.getHeaderName(), token.getToken());
		}

	}

	public record FamilySummary(UUID id, String name) {
	}

	public record MeResponse(UUID userId, String email, String displayName, MembershipRole role,
			FamilySummary family) {
	}

	public record LoginResponse(MeResponse user, CsrfResponse csrf) {
	}

	public record ChangePasswordRequest(@NotNull String currentPassword, @NotNull String newPassword) {
	}

}
