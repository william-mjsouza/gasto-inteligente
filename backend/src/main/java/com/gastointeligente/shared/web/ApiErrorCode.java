package com.gastointeligente.shared.web;

import org.springframework.http.HttpStatus;

/** Códigos estáveis de erro. O texto do código faz parte do contrato da API. */
public enum ApiErrorCode {

	MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Requisição em formato inválido."),
	VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Dados inválidos."),
	UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Autenticação necessária."),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "Acesso negado."),
	CSRF_INVALID(HttpStatus.FORBIDDEN, "Token CSRF ausente ou inválido."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "Recurso não encontrado."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Método não permitido para este recurso."),
	VERSION_CONFLICT(HttpStatus.CONFLICT,
			"O registro foi alterado por outra pessoa. Recarregue os dados e tente novamente."),
	CONFLICT(HttpStatus.CONFLICT, "A operação conflita com dados existentes."),
	DUPLICATE_NAME(HttpStatus.CONFLICT, "Já existe um registro com este nome."),
	EMAIL_UNAVAILABLE(HttpStatus.CONFLICT, "Não foi possível cadastrar este e-mail."),
	LAST_ADMIN(HttpStatus.CONFLICT, "A família precisa manter ao menos um administrador ativo."),
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de conteúdo não suportado."),
	TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas. Aguarde alguns minutos e tente novamente."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno. Informe o identificador da requisição ao suporte.");

	private final HttpStatus status;

	private final String defaultMessage;

	ApiErrorCode(HttpStatus status, String defaultMessage) {
		this.status = status;
		this.defaultMessage = defaultMessage;
	}

	public HttpStatus status() {
		return this.status;
	}

	public String defaultMessage() {
		return this.defaultMessage;
	}

}
