package com.gastointeligente.auth;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import com.gastointeligente.shared.audit.AuditService;
import com.gastointeligente.shared.web.ApiException;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Troca e redefinição de senha. Toda troca gera novo carimbo de segurança e revoga sessões. */
@Service
public class PasswordService {

	private final AppUserRepository users;

	private final PasswordEncoder encoder;

	private final LoginAttemptService attempts;

	private final AuditService audit;

	private final Clock clock;

	public PasswordService(AppUserRepository users, PasswordEncoder encoder, LoginAttemptService attempts,
			AuditService audit, Clock clock) {
		this.users = users;
		this.encoder = encoder;
		this.attempts = attempts;
		this.audit = audit;
		this.clock = clock;
	}

	/** @return novo carimbo de segurança, para manter válida apenas a sessão atual. */
	@Transactional
	public UUID changeOwnPassword(AppUserPrincipal principal, String currentPassword, String newPassword,
			String remoteAddr) {
		AppUser user = this.users.findById(principal.userId()).orElseThrow(ApiException::notFound);
		if (this.attempts.isBlocked(user.getEmail(), remoteAddr)) {
			throw new ApiException(com.gastointeligente.shared.web.ApiErrorCode.TOO_MANY_ATTEMPTS);
		}
		if (!this.encoder.matches(currentPassword, user.getPasswordHash())) {
			this.attempts.recordFailure(user.getEmail(), remoteAddr);
			throw ApiException.validation("currentPassword", "Senha atual incorreta.");
		}
		PasswordPolicy.validate("newPassword", newPassword);
		user.changePassword(this.encoder.encode(newPassword), this.clock.instant());
		this.audit.record(principal.familyId(), principal.userId(), "PASSWORD_CHANGED", "USER", user.getId(),
				Map.of());
		return user.getSecurityStamp();
	}

	/** Redefinição feita por administrador da mesma família ou pela recuperação administrativa. */
	@Transactional
	public void resetPassword(AppUser user, String newPassword, UUID familyId, UUID actorUserId) {
		PasswordPolicy.validate("newPassword", newPassword);
		user.changePassword(this.encoder.encode(newPassword), this.clock.instant());
		this.attempts.recordSuccess(user.getEmail());
		this.audit.record(familyId, actorUserId, "PASSWORD_RESET", "USER", user.getId(), Map.of());
	}

}
