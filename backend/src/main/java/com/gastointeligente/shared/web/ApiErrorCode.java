package com.gastointeligente.shared.web;

import org.springframework.http.HttpStatus;

/** Códigos estáveis de erro. O texto do código faz parte do contrato da API. */
public enum ApiErrorCode {

	MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Requisição em formato inválido."),
	VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Dados inválidos."),
	UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Autenticação necessária."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "Acesso negado."),
	CSRF_INVALID(HttpStatus.FORBIDDEN, "Token CSRF ausente ou inválido."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "Recurso não encontrado."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Método não permitido para este recurso."),
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de conteúdo não suportado."),
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
