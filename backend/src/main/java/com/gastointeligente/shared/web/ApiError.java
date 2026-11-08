package com.gastointeligente.shared.web;

import java.util.List;

/**
 * Corpo padrão de erro da API (seção 13): código estável, mensagem legível, itens afetados e
 * identificador da requisição. Nunca contém stack trace, SQL, senha ou conteúdo de arquivo.
 */
public record ApiError(String code, String message, List<Detail> details, String requestId) {

	public ApiError {
		details = details == null ? List.of() : List.copyOf(details);
	}

	/** Campo ou linha afetada. {@code row} é preenchido apenas em erros de importação. */
	public record Detail(String field, Integer row, String message) {
	}

}
