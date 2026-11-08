package com.gastointeligente.auth;

import java.nio.charset.StandardCharsets;

import com.gastointeligente.shared.web.ApiException;

/**
 * Regras mínimas de senha. O limite superior em bytes decorre do BCrypt, que considera apenas os
 * primeiros 72 bytes; senhas maiores são rejeitadas em vez de truncadas silenciosamente.
 */
public final class PasswordPolicy {

	public static final int MIN_LENGTH = 12;

	public static final int MAX_BYTES = 72;

	private PasswordPolicy() {
	}

	/** @return mensagem de violação ou {@code null} se a senha for aceitável. */
	public static String violation(String password) {
		if (password == null || password.isBlank()) {
			return "Informe a senha.";
		}
		if (password.codePointCount(0, password.length()) < MIN_LENGTH) {
			return "A senha deve ter pelo menos " + MIN_LENGTH + " caracteres.";
		}
		if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
			return "A senha deve ter no máximo " + MAX_BYTES + " bytes.";
		}
		if (!password.equals(password.strip())) {
			return "A senha não pode começar nem terminar com espaços.";
		}
		return null;
	}

	public static void validate(String field, String password) {
		String violation = violation(password);
		if (violation != null) {
			throw ApiException.validation(field, violation);
		}
	}

}
